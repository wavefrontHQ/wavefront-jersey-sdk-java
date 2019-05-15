package com.wavefront.agent;

import com.wavefront.transformer.JerseyEnvironmentTransformer;

import java.lang.instrument.Instrumentation;

public class WavefrontAgent {
  public static void premain(String agentArgs, Instrumentation inst) {
    inst.addTransformer(new JerseyEnvironmentTransformer());
  }
}
