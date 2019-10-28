package com.mindmercatis.dummyjdbc;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public aspect AspectLogger {

	pointcut traceMethods() : (execution(public * *(..))&& !cflow(within(AspectLogger)));

	before(): traceMethods() {
		Signature signature = thisJoinPointStaticPart.getSignature();
		Object target = thisJoinPoint.getTarget();
		if (target == null) {
			target = signature.getDeclaringType();
		}
		
		Logger logger = LoggerFactory.getLogger(target.getClass());
		logger.trace("Call {}.{}: ", signature.getDeclaringTypeName(), signature.getName());
		printParameters(logger, thisJoinPoint);
	}

	after() returning(Object o) : traceMethods() {
		Object target = thisJoinPoint.getTarget();
		if (target == null) {
			target = thisJoinPointStaticPart.getSignature().getDeclaringType();
		}
		LoggerFactory.getLogger(target.getClass()).trace("   -> Returning: {}", o);
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
