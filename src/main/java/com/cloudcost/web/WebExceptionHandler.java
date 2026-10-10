package com.cloudcost.web;

import com.cloudcost.common.InvalidRequestException;
import com.cloudcost.common.NotFoundException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders domain exceptions from server-side views on the shared error page. Only
 * messages from the platform's own exceptions are shown; everything else falls through
 * to Spring Boot's error handling, which renders a generic message.
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ModelAndView handleNotFound(NotFoundException ex) {
        return errorView(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidRequestException.class)
    ModelAndView handleInvalidRequest(InvalidRequestException ex) {
        return errorView(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ModelAndView errorView(HttpStatus status, String message) {
        ModelAndView view = new ModelAndView("error", status);
        view.addObject("status", status.value());
        view.addObject("error", status.getReasonPhrase());
        view.addObject("message", message);
        return view;
    }
}
