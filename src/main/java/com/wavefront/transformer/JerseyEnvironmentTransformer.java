package com.wavefront.transformer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

public class JerseyEnvironmentTransformer implements ClassFileTransformer {
  public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                          ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
    byte[] byteCode = classfileBuffer;
    if (className.equals("io/dropwizard/jersey/setup/JerseyEnvironment")) {
      try {
        ClassPool cp = ClassPool.getDefault();
        String curClassName = className.replaceAll("/", ".");
        CtClass curClass = cp.get(curClassName);
        for (CtConstructor constructor : curClass.getDeclaredConstructors()) {
          constructor.insertAfter("{register(new com.wavefront.sdk.jersey.WavefrontJerseyFactory().getWavefrontJerseyFilter());}");
        }
        return curClass.toBytecode();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    return byteCode;
  }
}
