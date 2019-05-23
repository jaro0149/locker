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

import java.util.concurrent.CountDownLatch;

final class CountUpDownLatch {
    private CountDownLatch latch;
    private final Object lock = new Object();

    CountUpDownLatch(final int count) {
        this.latch = new CountDownLatch(count);
    }

    void countDownOrWaitIfZero() {
        synchronized (lock) {
            while (latch.getCount() == 0) {
                try {
                    lock.wait();
                } catch (final InterruptedException e) {
                    throw new IllegalStateException("Unexpected interruption on count-down-or-wait-if-zero " +
                        "has occurred.", e);
                }
            }
            latch.countDown();
            lock.notifyAll();
        }
    }

    void waitUntilZero() {
        synchronized (lock) {
            while (latch.getCount() != 0) {
                try {
                    lock.wait();
                } catch (final InterruptedException e) {
                    throw new IllegalStateException("Unexpected interruption on latch wait-for-zero operation " +
                        "has occurred.", e);
                }
            }
        }
    }

    void countUp() {
        synchronized (lock) {
            latch = new CountDownLatch((int) latch.getCount() + 1);
            lock.notifyAll();
        }
    }
}