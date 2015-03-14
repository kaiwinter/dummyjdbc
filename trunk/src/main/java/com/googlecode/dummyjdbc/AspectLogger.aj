package com.googlecode.dummyjdbc;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public aspect AspectLogger {

	pointcut traceMethods() : (execution(public * *(..))&& !cflow(within(AspectLogger)));

	before(): traceMethods() {
		Signature sig = thisJoinPointStaticPart.getSignature();

		Logger logger = LoggerFactory.getLogger(thisJoinPoint.getTarget().getClass());
		logger.trace("Call {}.{}: ", sig.getDeclaringTypeName(), sig.getName());
		printParameters(logger, thisJoinPoint);
	}

	after() returning(Object o) : traceMethods() {
		LoggerFactory.getLogger(thisJoinPoint.getTarget().getClass()).trace("   -> Returning: {}", o);
	}

	static private void printParameters(Logger logger, JoinPoint jp) {
		Object[] args = jp.getArgs();
		String[] names = ((CodeSignature) jp.getSignature()).getParameterNames();
		Class<?>[] types = ((CodeSignature) jp.getSignature()).getParameterTypes();
		for (int i = 0; i < args.length; i++) {
			logger.trace("   {}: {} = {}", names[i], types[i].getName(), args[i]);
		}
	}
}
