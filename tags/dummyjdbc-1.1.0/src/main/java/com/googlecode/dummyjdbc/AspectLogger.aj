package com.googlecode.dummyjdbc;

import java.text.MessageFormat;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;

public aspect AspectLogger {

	pointcut traceMethods() : (execution(public * *(..))&& !cflow(within(AspectLogger)));

	before(): traceMethods() {
		Signature sig = thisJoinPointStaticPart.getSignature();

		String message = "Call " + sig.getDeclaringTypeName() + "." + sig.getName() + ": ";
		System.err.println(message);
		printParameters(thisJoinPoint);
	}

	after() returning(Object o) : traceMethods() {
		System.err.println("   -> Returning: " + o);
	}

	static private void printParameters(JoinPoint jp) {
		Object[] args = jp.getArgs();
		String[] names = ((CodeSignature) jp.getSignature()).getParameterNames();
		Class<?>[] types = ((CodeSignature) jp.getSignature()).getParameterTypes();
		for (int i = 0; i < args.length; i++) {
			System.err.println(MessageFormat.format("   {0}: {1} = {2}", names[i], types[i].getName(), args[i]));
		}
	}
}
