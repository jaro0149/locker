/* Copyright (C) 2019 Jaroslav Tóth

   This program is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.*/

package sk.jdecors.locker.aspects;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class LockData {

    enum CurrentThreadLockState {
        UNLOCKED,
        READ_ONLY_LOCKED,
        READ_WRITE_LOCKED
    }

    private static final class LockSourceInformation {
        private final long stamp;
        private final boolean cameFromUpgradeProcess;
        private final boolean cameFromDowngradeProcess;

        private LockSourceInformation(final long stamp, final boolean cameFromUpgradeProcess,
            final boolean cameFromDowngradeProcess) {
            this.stamp = stamp;
            this.cameFromUpgradeProcess = cameFromUpgradeProcess;
            this.cameFromDowngradeProcess = cameFromDowngradeProcess;
        }
    }

    private final StampedLockWithBarrier stampedLock;
    private final Map<Thread, LockSourceInformation> currentThreads = new ConcurrentHashMap<>();

    LockData(final StampedLockWithBarrier stampedLock) {
        this.stampedLock = stampedLock;
    }

    void allocateReadLock() {
        final long stamp = stampedLock.readLock();
        currentThreads.put(Thread.currentThread(), new LockSourceInformation(stamp, false, false));
    }

    void allocateWriteLock() {
        final Thread currentThread = Thread.currentThread();
        if (getLockStateInCurrentThread().equals(CurrentThreadLockState.READ_ONLY_LOCKED)) {
            final long stamp = stampedLock.upgradeLock(currentThreads.get(currentThread).stamp);
            currentThreads.replace(currentThread, new LockSourceInformation(stamp, true, false));
        } else {
            final long stamp = stampedLock.writeLock();
            currentThreads.put(currentThread, new LockSourceInformation(stamp, false, false));
        }
    }

    void unlockReadLock() {
        final Thread currentThread = Thread.currentThread();
        final LockSourceInformation lockInformation = currentThreads.get(currentThread);
        stampedLock.unlockRead(lockInformation.stamp, lockInformation.cameFromDowngradeProcess);
        currentThreads.remove(currentThread);
    }

    void unlockWriteLock() {
        final Thread currentThread = Thread.currentThread();
        final LockSourceInformation lockInformation = currentThreads.get(currentThread);
        if (lockInformation.cameFromUpgradeProcess) {
            final long stamp = stampedLock.downgradeLock(currentThreads.get(currentThread).stamp);
            currentThreads.replace(currentThread, new LockSourceInformation(stamp, false, true));
        } else {
            stampedLock.unlockWrite(currentThreads.get(currentThread).stamp);
            currentThreads.remove(currentThread);
        }
    }

    CurrentThreadLockState getLockStateInCurrentThread() {
        if (currentThreads.containsKey(Thread.currentThread())) {
            if (stampedLock.isWriteLocked()) {
                return CurrentThreadLockState.READ_WRITE_LOCKED;
            } else {
                return CurrentThreadLockState.READ_ONLY_LOCKED;
            }
        } else {
            return CurrentThreadLockState.UNLOCKED;
        }
    }
}