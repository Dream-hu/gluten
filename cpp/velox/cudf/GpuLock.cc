/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "GpuLock.h"
#include <mutex>
#include <condition_variable>
#include <optional>
#include <stdexcept>

namespace gluten {

namespace {
struct GpuLockState {
  std::mutex gGpuMutex;
  std::condition_variable gGpuCv;
  std::optional<std::thread::id> gGpuOwner;
};

GpuLockState& getGpuLockState() {
  static GpuLockState gGpuLockState;
  return gGpuLockState;
}
}

void lockGpu() {
    std::thread::id tid = std::this_thread::get_id();
    std::unique_lock<std::mutex> lock(getGpuLockState().gGpuMutex);
    if (getGpuLockState().gGpuOwner == tid) {
        // Reentrant call from the same thread — do nothing
        return;
    }


    // Wait until the GPU lock becomes available
    getGpuLockState().gGpuCv.wait(lock, [] {
        return !getGpuLockState().gGpuOwner.has_value();
    });

    // Acquire ownership
    getGpuLockState().gGpuOwner = tid;
}

void unlockGpu() {
    std::thread::id tid = std::this_thread::get_id();
    std::unique_lock<std::mutex> lock(getGpuLockState().gGpuMutex);
    if (!getGpuLockState().gGpuOwner.has_value() || getGpuLockState().gGpuOwner != tid) {
        throw std::runtime_error("unlockGpu() called by non-owner thread!");
    }

    // Release ownership
    getGpuLockState().gGpuOwner = std::nullopt;

    // Notify one waiting thread
    lock.unlock();
    getGpuLockState().gGpuCv.notify_one();
}


} // namespace gluten
