/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.client;

import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import edu.brown.cs.systems.baggage.DetachedBaggage;
import edu.brown.cs.systems.baggage.Baggage;
import edu.brown.cs.systems.xtrace.XTrace;
import org.apache.hadoop.hbase.trace.TraceUtil;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hadoop.hbase.util.EnvironmentEdgeManager;

/**
 * A completion service for the RpcRetryingCallerFactory.
 * Keeps the list of the futures, and allows to cancel them all.
 * This means as well that it can be used for a small set of tasks only.
 * <br>Implementation is not Thread safe.
 *
 * CompletedTasks is implemented as a queue, the entry is added based on the time order. I.e,
 * when the first task completes (whether it is a success or failure), it is added as a first
 * entry in the queue, the next completed task is added as a second entry in the queue, ...
 * When iterating through the queue, we know it is based on time order. If the first
 * completed task succeeds, it is returned. If it is failure, the iteration goes on until it
 * finds a success.
 */
@InterfaceAudience.Private
public class ResultBoundedCompletionService<V> {
  private static final Logger LOG = LoggerFactory.getLogger(ResultBoundedCompletionService.class);
  private final RpcRetryingCallerFactory retryingCallerFactory;
  private final Executor executor;
  private final QueueingFuture<V>[] tasks; // all the tasks
  private final ArrayList<QueueingFuture> completedTasks; // completed tasks
  private volatile boolean cancelled = false;
  
  class QueueingFuture<T> implements RunnableFuture<T> {
    private final RetryingCallable<T> future;
    private T result = null;
    private ExecutionException exeEx = null;
    private volatile boolean cancelled = false;
    private final int callTimeout;
    private final RpcRetryingCaller<T> retryingCaller;
    private boolean resultObtained = false;
    private final int replicaId;  // replica id
    public volatile DetachedBaggage bag = null;


    public QueueingFuture(RetryingCallable<T> future, int callTimeout, int id) {
      this.future = future;
      this.callTimeout = callTimeout;
      this.retryingCaller = retryingCallerFactory.<T>newCaller();
      this.replicaId = id;
    }


    // TODO HBASE seems to have a datarace here: access result, result obtained from different threads concurrently, no lock
    @SuppressWarnings("unchecked")
    @Override
    public void run() {
      try {
        Baggage.start(bag);
        // XTRACE we need this ugly redundant code because this is not properly locked
        if (!cancelled) {

          result = this.retryingCaller.callWithRetries(future, callTimeout);
          QueueingFuture.this.bag = Baggage.fork();
          resultObtained = true;
        }
      } catch (Throwable t) {
        QueueingFuture.this.bag = Baggage.fork();
        exeEx = new ExecutionException(t);
      } finally {
        synchronized (tasks) {

          // If this wasn't canceled then store the result.
          if (!cancelled) {
            completedTasks.add(QueueingFuture.this);
          }

          // Notify just in case there was someone waiting and this was canceled.
          // That shouldn't happen but better safe than sorry.
          tasks.notify();
          Baggage.discard();
        }
      }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
      if (resultObtained || exeEx != null) return false;
      retryingCaller.cancel();
      if (future instanceof Cancellable) ((Cancellable)future).cancel();
      cancelled = true;
      Baggage.start(bag);
      // XTRACE start/join makes sense, if a thread other than the "submit" thread is calling cancel here
      // otherwise we just join the baggage we alread have in the thread
      return true;
    }

    @Override
    public boolean isCancelled() {
      return cancelled;
    }

    @Override
    public boolean isDone() {
      return resultObtained || exeEx != null;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
      try {
        return get(1000, TimeUnit.DAYS);
      } catch (TimeoutException e) {
        // TODO HBASE this is not a conversation?
        throw new RuntimeException("You did wait for 1000 days here?", e);
      }
    }

    @Override
    public T get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException {
      synchronized (tasks) {
        // XTRACE we need this ugly redundant code because this is not properly locked
        if (resultObtained) {
          Baggage.start(bag);
          return result;
        }
        if (exeEx != null) {
          Baggage.start(bag);
          throw exeEx;
        }
        unit.timedWait(tasks, timeout);
      }
      if (resultObtained) {
        Baggage.start(bag);

        return result;
      }
      if (exeEx != null) {
        Baggage.start(bag);
        throw exeEx;
      }

      throw new TimeoutException("timeout=" + timeout + ", " + unit);
    }

    public int getReplicaId() {
      return replicaId;
    }

    public ExecutionException getExeEx() {
      return exeEx;
    }
  }

  @SuppressWarnings("unchecked")
  public ResultBoundedCompletionService(
      RpcRetryingCallerFactory retryingCallerFactory, Executor executor,
      int maxTasks) {
    this.retryingCallerFactory = retryingCallerFactory;
    this.executor = executor;
    this.tasks = new QueueingFuture[maxTasks];
    this.completedTasks = new ArrayList<>(maxTasks);
  }


  public void submit(RetryingCallable<V> task, int callTimeout, int id) {
    QueueingFuture<V> newFuture = new QueueingFuture<>(task, callTimeout, id);
    newFuture.bag= Baggage.fork();
    executor.execute(TraceUtil.wrap(newFuture, "ResultBoundedCompletionService.submit"));
    tasks[id] = newFuture;
  }

  public QueueingFuture<V> take() throws InterruptedException {
    synchronized (tasks) {
      while (!cancelled && (completedTasks.size() < 1)) tasks.wait();
    }
    return completedTasks.get(0);
  }

  /**
   * Poll for the first completed task whether it is a success or execution exception.
   *
   * @param timeout  - time to wait before it times out
   * @param unit  - time unit for timeout
   */
  public QueueingFuture<V> poll(long timeout, TimeUnit unit) throws InterruptedException {
    return pollForSpecificCompletedTask(timeout, unit, 0);
  }

  /**
   * Poll for the first successfully completed task whose completed order is in startIndex,
   * endIndex(exclusive) range
   *
   * @param timeout  - time to wait before it times out
   * @param unit  - time unit for timeout
   * @param startIndex - start index, starting from 0, inclusive
   * @param endIndex - end index, exclusive
   *
   * @return If within timeout time, there is no successfully completed task, return null; If all
   *         tasks get execution exception, it will throw out the last execution exception,
   *         otherwise return the first successfully completed task's result.
   */
  public QueueingFuture<V> pollForFirstSuccessfullyCompletedTask(long timeout, TimeUnit unit,
      int startIndex, int endIndex)
      throws InterruptedException, CancellationException, ExecutionException {

    QueueingFuture<V>  f;
    long start, duration;
    for (int i = startIndex; i < endIndex; i ++) {

      start = EnvironmentEdgeManager.currentTime();
      f = pollForSpecificCompletedTask(timeout, unit, i);
      duration = EnvironmentEdgeManager.currentTime() - start;

      // Even with operationTimeout less than 0, still loop through the rest as there could
      // be other completed tasks before operationTimeout.
      timeout -= duration;

      if (f == null) {
        return null;
      } else if (f.getExeEx() != null) {
        // we continue here as we need to loop through all the results.
        if (LOG.isDebugEnabled()) {
          LOG.debug("Replica " + ((f == null) ? 0 : f.getReplicaId()) + " returns " +
              f.getExeEx().getCause());
        }

        if (i == (endIndex - 1)) {
          // Rethrow this exception
          throw f.getExeEx();
        }
        continue;
      }

      return f;
    }

    // impossible to reach
    return null;
  }

  /**
   * Poll for the Nth completed task (index starts from 0 (the 1st), 1 (the second)...)
   *
   * @param timeout  - time to wait before it times out
   * @param unit  - time unit for timeout
   * @param index - the index(th) completed task, index starting from 0
   */
  private QueueingFuture<V> pollForSpecificCompletedTask(long timeout, TimeUnit unit, int index)
      throws InterruptedException {
    if (index < 0) {
      return null;
    }

    synchronized (tasks) {
      if (!cancelled && (completedTasks.size() <= index)) unit.timedWait(tasks, timeout);
      if (completedTasks.size() <= index) return null;
    }
    return completedTasks.get(index);
  }

  public void cancelAll() {
    // Grab the lock on tasks so that cancelled is visible everywhere
    synchronized (tasks) {
      cancelled = true;
    }
    for (QueueingFuture<V> future : tasks) {
      if (future != null) future.cancel(true);
    }
  }
}
