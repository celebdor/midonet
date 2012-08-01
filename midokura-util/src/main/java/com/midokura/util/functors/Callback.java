/*
* Copyright 2012 Midokura Europe SARL
*/
package com.midokura.util.functors;

/**
 * // TODO: mtoader ! Please explain yourself.
 */
public interface Callback<T, E extends Exception> {

    public void onSuccess(T data);

    public void onTimeout();

    public void onError(E e);

    public interface MultiResult<T> extends Iterable<Result<T>> {

        public boolean hasTimeouts();

        public boolean hasExceptions();
    }

    public interface Result<T> {

        public String operation();

        public T getData();

        public boolean timeout();

        public Exception exception();
    }
}
