/*
 * Copyright 2012 Midokura PTE LTD.
 */
package com.midokura.midolman.mgmt.rest_api.jaxrs;

import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * WebApplicationException class to represent 400 status.
 */
public class BadRequestHttpException extends WebApplicationException {

    private static final long serialVersionUID = 1L;

    /**
     * Create a BadRequestHttpException object with no message.
     */
    public BadRequestHttpException() {
        this("");
    }

    /**
     * Create a BadRequestHttpException object with a message.
     *
     * @param message
     *            Error message.
     */
    public BadRequestHttpException(String message) {
        super(ResponseUtils.buildErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(), message));
    }

    /**
     * Create a BadRequestHttpException object with a message.
     *
     * @param e
     *            Throwable object
     * @param message
     *            Error message.
     */
    public BadRequestHttpException(Throwable e, String message) {
        super(e, ResponseUtils.buildErrorResponse(
                Response.Status.BAD_REQUEST.getStatusCode(), message));
    }

    public <T> BadRequestHttpException(Set<ConstraintViolation<T>> violations) {
        super(ResponseUtils.buildValidationErrorResponse(violations));
    }
}
