package com.liveramp.cascading_ext;

import cascading.flow.FlowConnector;
import cascading.flow.FlowProcess;
import cascading.flow.FlowStepStrategy;
import cascading.flow.hadoop.HadoopFlowProcess;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.serializer.Serialization;
import org.apache.hadoop.mapred.JobConf;

import java.util.*;

public class CascadingHelper {
  private static final CascadingHelper instance = new CascadingHelper();

  public static CascadingHelper get() {
    return instance;
  }

  protected CascadingHelper(){}

  private final Set<Class<? extends Serialization>> serializations = new HashSet<Class<? extends Serialization>>();
  private final Map<Integer, Class<?>> serializationTokens = new HashMap<Integer, Class<?>>();
  private final Map<Object, Object> defaultProperties = new HashMap<Object, Object>();
  private final List<FlowStepStrategyFactory<JobConf>> defaultFlowStepStrategies = new ArrayList<FlowStepStrategyFactory<JobConf>>();
  private transient JobConf conf = null;

  protected void setDefaultProperty(Object key, Object value){
    defaultProperties.put(key, value);
    conf = null;
  }

  protected void addSerialization(Class<? extends Serialization> serialization){
    serializations.add(serialization);
    conf = null;
  }

  protected void addSerializationToken(int token, Class<?> klass){
    if(token <= 128)
      throw new IllegalArgumentException("Serialization tokens must be greater than 128 (lower numbers are reserved by Cascading)");

    if(serializationTokens.containsKey(token) && !serializationTokens.get(token).equals(klass))
      throw new IllegalArgumentException("Token " + token + " is already assigned to class " + serializationTokens.get(token));

    serializationTokens.put(token, klass);
  }

  protected void addDefaultFlowStepStrategy(FlowStepStrategyFactory<JobConf> flowStepStrategyFactory){
    defaultFlowStepStrategies.add(flowStepStrategyFactory);
  }

  protected void addDefaultFlowStepStrategy(Class<? extends FlowStepStrategy<JobConf>> klass){
    defaultFlowStepStrategies.add(new SimpleFlowStepStrategyFactory(klass));
  }

  private String getSerializationsProperty(){
    // Get the existing serializations
    List<String> strings = new ArrayList<String>();

    String existing = new JobConf().get("io.serializations");
    if(existing != null)
      strings.add(existing);

    // Append our custom serializations
    for(Class<? extends Serialization> klass : serializations){
      strings.add(klass.getName());
    }

    return StringUtils.join(strings, ",");
  }

  private String getSerializationTokensProperty(){
    List<String> strings = new ArrayList<String>();
    for(Map.Entry<Integer, Class<?>> entry : serializationTokens.entrySet()){
      strings.add(entry.getKey() + "=" + entry.getValue().getName());
    }
    return StringUtils.join(strings, ",");
  }

  public JobConf getJobConf(){
    if(conf == null){
      conf = new JobConf();
      conf.set("io.serializations", getSerializationsProperty());
      conf.set("cascading.serialization.tokens", getSerializationTokensProperty());
    }
    return new JobConf(conf);
  }

  public FlowConnector getFlowConnector() {
    return getFlowConnector(Collections.<Object, Object>emptyMap());
  }

  public FlowConnector getFlowConnector(Map<Object, Object> properties) {
    return getFlowConnector(properties, Collections.<FlowStepStrategy<JobConf>>emptyList());
  }

  public FlowConnector getFlowConnector(List<FlowStepStrategy<JobConf>> flowStepStrategies) {
    return getFlowConnector(Collections.<Object, Object>emptyMap(), flowStepStrategies);
  }

  public FlowConnector getFlowConnector(Map<Object, Object> properties, List<FlowStepStrategy<JobConf>> flowStepStrategies) {
    //Add in default properties
    Map<Object, Object> combinedProperties = new HashMap<Object, Object>(properties);
    combinedProperties.putAll(defaultProperties);
    combinedProperties.put("io.serializations", getSerializationsProperty());
    combinedProperties.put("cascading.serialization.tokens", getSerializationTokensProperty());

    //Add in default flow step strategies
    List<FlowStepStrategy<JobConf>> combinedStrategies = new ArrayList<FlowStepStrategy<JobConf>>(flowStepStrategies);
    for(FlowStepStrategyFactory<JobConf> flowStepStrategyFactory : defaultFlowStepStrategies){
      combinedStrategies.add(flowStepStrategyFactory.getFlowStepStrategy());
    }

    return new LoggingFlowConnector(combinedProperties, new MultiFlowStepStrategy(combinedStrategies));
  }

  public FlowProcess<JobConf> getFlowProcess() {
    return getFlowProcess(getJobConf());
  }

  public FlowProcess<JobConf> getFlowProcess(JobConf jobConf) {
    return new HadoopFlowProcess(jobConf);
  }
}