/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.ipc;

import boundarydetection.tracker.AccessTracker;
import boundarydetection.tracker.tasks.Task;
import boundarydetection.tracker.tasks.TaskCollisionException;
import org.apache.hbase.thirdparty.com.google.protobuf.RpcCallback;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.apache.yetus.audience.InterfaceAudience;


/**
 * Simple {@link RpcCallback} implementation providing a
 * {@link java.util.concurrent.Future}-like {@link BlockingRpcCallback#get()} method, which
 * will block util the instance's {@link BlockingRpcCallback#run(Object)} method has been called.
 * {@code R} is the RPC response type that will be passed to the {@link #run(Object)} method.
 */
@InterfaceAudience.Private
public class BlockingRpcCallback<R> implements RpcCallback<R> {
  private R result;
  private boolean resultSet = false;
//  private volatile DetachedBaggage bag = null;
  private volatile Task task = null;
  /**
   * Called on completion of the RPC call with the response object, or {@code null} in the case of
   * an error.
   * @param parameter the response object or {@code null} if an error occurred
   */
  @Override
  public void run(R parameter) {
    synchronized (this) {
      result = parameter;
      resultSet = true;
      //bag = Baggage.fork();
      task = AccessTracker.fork();
      this.notifyAll();
    }
  }

  /**
   * Returns the parameter passed to {@link #run(Object)} or {@code null} if a null value was
   * passed.  When used asynchronously, this method will block until the {@link #run(Object)}
   * method has been called.
   * @return the response object or {@code null} if no response was passed
   */
  public synchronized R get() throws IOException {
    while (!resultSet) {
      try {
        this.wait();
      } catch (InterruptedException ie) {
        InterruptedIOException exception = new InterruptedIOException(ie.getMessage());
        exception.initCause(ie);
        throw exception;
      }
    }
//    Baggage.join(bag);
    if(task!=null){
      try {
        AccessTracker.tryJoin(task);
        AccessTracker.getTask().setWriteCapability(true);
        AccessTracker.getTask().setTag("RPCCallback");
      }catch (TaskCollisionException e){
        //In case of a collision we declassfiy here explicitly and add the joiner because we join to the thread that
        // started a request in HBaseAdmin or HTable. Collision is normal because we start a new task when we got the
        // response over network
        AccessTracker.getTask().addJoiner(task);
      }
    }
    return result;
  }
}
