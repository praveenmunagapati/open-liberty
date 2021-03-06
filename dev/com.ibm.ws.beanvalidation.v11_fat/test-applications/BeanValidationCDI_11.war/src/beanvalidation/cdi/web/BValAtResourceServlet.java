/*******************************************************************************
 * Copyright (c) 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package beanvalidation.cdi.web;

import javax.annotation.Resource;
import javax.servlet.annotation.WebServlet;
import javax.validation.ValidatorFactory;

import componenttest.app.FATServlet;

/**
 * Servlet that obtains a ValidatorFactory through using the @Resource annotation.
 */
@WebServlet("/BValAtResourceServlet")
@SuppressWarnings("serial")
public class BValAtResourceServlet extends FATServlet {

    @Resource
    ValidatorFactory resourceValidatorFactory;

    /**
     * Test that a ValidatorFactory obtained with @Resource has the custom message
     * interpolator set and that the custom message interpolator was able to inject
     * another bean in this application.
     */
    public void testCDIInjectionInInterpolatorAtResource() throws Exception {
        String interpolatedValue = resourceValidatorFactory.getMessageInterpolator().interpolate(null, null);

        if (!interpolatedValue.equals("something")) {
            throw new Exception("custom interpolator should have returned the value " +
                                "'something', but returned : " + interpolatedValue);
        }

    }

}