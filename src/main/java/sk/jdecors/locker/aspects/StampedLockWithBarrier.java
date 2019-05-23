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

import java.util.concurrent.locks.StampedLock;

final class StampedLockWithBarrier {
    private final StampedLock stampedLock = new StampedLock();
    private final CountUpDownLatch barrier = new CountUpDownLatch(0);

    long readLock() {
        barrier.waitUntilZero();
        return stampedLock.readLock();
    }

    long writeLock() {
        barrier.waitUntilZero();
        return stampedLock.writeLock();
    }

    void unlockRead(final long stamp, final boolean cameFromDowngradeProcess) {
        stampedLock.unlockRead(stamp);
        if (cameFromDowngradeProcess) {
            barrier.countDownOrWaitIfZero();
        }
    }

    void unlockWrite(final long stamp) {
        stampedLock.unlockWrite(stamp);
    }

    long upgradeLock(final long stamp) {
        barrier.countUp();
        stampedLock.unlockRead(stamp);
        return stampedLock.writeLock();
    }

    long downgradeLock(final long stamp) {
        return stampedLock.tryConvertToReadLock(stamp);
    }

    boolean isWriteLocked() {
        return stampedLock.isWriteLocked();
    }
}