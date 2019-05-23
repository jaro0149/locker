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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import sk.jdecors.locker.annotations.LockedForRead;
import sk.jdecors.locker.annotations.LockedForWrite;
import sk.jdecors.locker.aspects.LockData.CurrentThreadLockState;

@Aspect("perthis(executionContext())")
public final class ReadWriteLocker {

    private final Map<Integer, LockData> locksMap = new ConcurrentHashMap<>();

    @Pointcut("cflow(within(sk.jdecors.locker.aspects..*))")
    public void deprecatedAspectTraces() {
    }

    @Pointcut("execution(* *(..))")
    public void executionOfAllMethods() {
    }

    @Pointcut("@annotation(sk.jdecors.locker.annotations.LockedForRead)")
    public void readOnlyMethods() {
    }

    @Pointcut("@annotation(sk.jdecors.locker.annotations.LockedForWrite)")
    public void readWriteMethods() {
    }

    @Pointcut("!deprecatedAspectTraces() && executionOfAllMethods() && readOnlyMethods()")
    public void executionOfReadOnlyMethods() {
    }

    @Pointcut("!deprecatedAspectTraces() && executionOfAllMethods() && readWriteMethods()")
    public void executionOfReadWriteMethods() {
    }

    @Pointcut("readOnlyMethods() || readWriteMethods()")
    public void executionContext() {
    }

    @Around("executionOfReadOnlyMethods()")
    public Object readLock(final ProceedingJoinPoint joinPoint) throws Throwable {
        final List<LockData> lockData = allocateReadLocks(joinPoint);
        try {
            return joinPoint.proceed();
        } finally {
            unlockReadLocks(lockData);
        }
    }

    @Around("executionOfReadWriteMethods()")
    public Object writeLock(final ProceedingJoinPoint joinPoint) throws Throwable {
        final List<LockData> lockData = allocateWriteLocks(joinPoint);
        try {
            return joinPoint.proceed();
        } finally {
            unlockWriteLocks(lockData);
        }
    }

    private List<LockData> allocateReadLocks(final ProceedingJoinPoint joinPoint) {
        return getStampedLocksForReadLocks(joinPoint).stream()
            .filter(lock -> lock.getLockStateInCurrentThread().equals(CurrentThreadLockState.UNLOCKED))
            .peek(LockData::allocateReadLock)
            .collect(Collectors.toList());
    }

    private List<LockData> allocateWriteLocks(final ProceedingJoinPoint joinPoint) {
        return getStampedLocksForWriteLocks(joinPoint).stream()
            .filter(lock -> !lock.getLockStateInCurrentThread().equals(CurrentThreadLockState.READ_WRITE_LOCKED))
            .peek(LockData::allocateWriteLock)
            .collect(Collectors.toList());
    }

    private void unlockReadLocks(final List<LockData> lockData) {
        for (int i = lockData.size() - 1; i >= 0; i--) {
            lockData.get(i).unlockReadLock();
        }
    }

    private void unlockWriteLocks(final List<LockData> lockData) {
        for (int i = lockData.size() - 1; i >= 0; i--) {
            lockData.get(i).unlockWriteLock();
        }
    }

    private List<LockData> getStampedLocksForReadLocks(final ProceedingJoinPoint joinPoint) {
        final Method method = getMethod(joinPoint);
        final LockedForRead annotation = method.getAnnotation(LockedForRead.class);
        return collectStampedLocks(annotation.stamp());
    }

    private List<LockData> getStampedLocksForWriteLocks(final ProceedingJoinPoint joinPoint) {
        final Method method = getMethod(joinPoint);
        final LockedForWrite annotation = method.getAnnotation(LockedForWrite.class);
        return collectStampedLocks(annotation.stamp());
    }

    private Method getMethod(final ProceedingJoinPoint joinPoint) {
        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getMethod();
    }

    private List<LockData> collectStampedLocks(final int[] stamps) {
        return reduceStamps(stamps).stream()
            .map(stamp -> locksMap.computeIfAbsent(stamp, s -> new LockData(new StampedLockWithBarrier())))
            .collect(Collectors.toCollection(LinkedList::new));
    }

    private static List<Integer> reduceStamps(final int[] stamps) {
        final List<Integer> outputList = new ArrayList<>();
        for (int stamp : stamps) {
            if (!outputList.contains(stamp)) {
                outputList.add(stamp);
            }
        }
        return outputList;
    }
}