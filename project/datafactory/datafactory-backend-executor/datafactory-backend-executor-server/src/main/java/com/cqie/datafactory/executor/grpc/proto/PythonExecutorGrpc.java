package com.cqie.datafactory.executor.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.68.1)",
    comments = "Source: executor.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class PythonExecutorGrpc {

  private PythonExecutorGrpc() {}

  public static final java.lang.String SERVICE_NAME = "executor.PythonExecutor";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.ExecuteRequest,
      com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> getExecuteMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Execute",
      requestType = com.cqie.datafactory.executor.grpc.proto.ExecuteRequest.class,
      responseType = com.cqie.datafactory.executor.grpc.proto.ExecuteResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.ExecuteRequest,
      com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> getExecuteMethod() {
    io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.ExecuteRequest, com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> getExecuteMethod;
    if ((getExecuteMethod = PythonExecutorGrpc.getExecuteMethod) == null) {
      synchronized (PythonExecutorGrpc.class) {
        if ((getExecuteMethod = PythonExecutorGrpc.getExecuteMethod) == null) {
          PythonExecutorGrpc.getExecuteMethod = getExecuteMethod =
              io.grpc.MethodDescriptor.<com.cqie.datafactory.executor.grpc.proto.ExecuteRequest, com.cqie.datafactory.executor.grpc.proto.ExecuteResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Execute"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cqie.datafactory.executor.grpc.proto.ExecuteRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cqie.datafactory.executor.grpc.proto.ExecuteResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PythonExecutorMethodDescriptorSupplier("Execute"))
              .build();
        }
      }
    }
    return getExecuteMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest,
      com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> getHealthCheckMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "HealthCheck",
      requestType = com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest.class,
      responseType = com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest,
      com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> getHealthCheckMethod() {
    io.grpc.MethodDescriptor<com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest, com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> getHealthCheckMethod;
    if ((getHealthCheckMethod = PythonExecutorGrpc.getHealthCheckMethod) == null) {
      synchronized (PythonExecutorGrpc.class) {
        if ((getHealthCheckMethod = PythonExecutorGrpc.getHealthCheckMethod) == null) {
          PythonExecutorGrpc.getHealthCheckMethod = getHealthCheckMethod =
              io.grpc.MethodDescriptor.<com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest, com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "HealthCheck"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PythonExecutorMethodDescriptorSupplier("HealthCheck"))
              .build();
        }
      }
    }
    return getHealthCheckMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PythonExecutorStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PythonExecutorStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PythonExecutorStub>() {
        @java.lang.Override
        public PythonExecutorStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PythonExecutorStub(channel, callOptions);
        }
      };
    return PythonExecutorStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PythonExecutorBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PythonExecutorBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PythonExecutorBlockingStub>() {
        @java.lang.Override
        public PythonExecutorBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PythonExecutorBlockingStub(channel, callOptions);
        }
      };
    return PythonExecutorBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PythonExecutorFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PythonExecutorFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PythonExecutorFutureStub>() {
        @java.lang.Override
        public PythonExecutorFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PythonExecutorFutureStub(channel, callOptions);
        }
      };
    return PythonExecutorFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void execute(com.cqie.datafactory.executor.grpc.proto.ExecuteRequest request,
        io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getExecuteMethod(), responseObserver);
    }

    /**
     */
    default void healthCheck(com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest request,
        io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHealthCheckMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PythonExecutor.
   */
  public static abstract class PythonExecutorImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PythonExecutorGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PythonExecutor.
   */
  public static final class PythonExecutorStub
      extends io.grpc.stub.AbstractAsyncStub<PythonExecutorStub> {
    private PythonExecutorStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonExecutorStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PythonExecutorStub(channel, callOptions);
    }

    /**
     */
    public void execute(com.cqie.datafactory.executor.grpc.proto.ExecuteRequest request,
        io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getExecuteMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void healthCheck(com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest request,
        io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHealthCheckMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PythonExecutor.
   */
  public static final class PythonExecutorBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PythonExecutorBlockingStub> {
    private PythonExecutorBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonExecutorBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PythonExecutorBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.cqie.datafactory.executor.grpc.proto.ExecuteResponse execute(com.cqie.datafactory.executor.grpc.proto.ExecuteRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getExecuteMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse healthCheck(com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHealthCheckMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PythonExecutor.
   */
  public static final class PythonExecutorFutureStub
      extends io.grpc.stub.AbstractFutureStub<PythonExecutorFutureStub> {
    private PythonExecutorFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PythonExecutorFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PythonExecutorFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.cqie.datafactory.executor.grpc.proto.ExecuteResponse> execute(
        com.cqie.datafactory.executor.grpc.proto.ExecuteRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getExecuteMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse> healthCheck(
        com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHealthCheckMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_EXECUTE = 0;
  private static final int METHODID_HEALTH_CHECK = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_EXECUTE:
          serviceImpl.execute((com.cqie.datafactory.executor.grpc.proto.ExecuteRequest) request,
              (io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.ExecuteResponse>) responseObserver);
          break;
        case METHODID_HEALTH_CHECK:
          serviceImpl.healthCheck((com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest) request,
              (io.grpc.stub.StreamObserver<com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getExecuteMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.cqie.datafactory.executor.grpc.proto.ExecuteRequest,
              com.cqie.datafactory.executor.grpc.proto.ExecuteResponse>(
                service, METHODID_EXECUTE)))
        .addMethod(
          getHealthCheckMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.cqie.datafactory.executor.grpc.proto.HealthCheckRequest,
              com.cqie.datafactory.executor.grpc.proto.HealthCheckResponse>(
                service, METHODID_HEALTH_CHECK)))
        .build();
  }

  private static abstract class PythonExecutorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PythonExecutorBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.cqie.datafactory.executor.grpc.proto.Executor.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PythonExecutor");
    }
  }

  private static final class PythonExecutorFileDescriptorSupplier
      extends PythonExecutorBaseDescriptorSupplier {
    PythonExecutorFileDescriptorSupplier() {}
  }

  private static final class PythonExecutorMethodDescriptorSupplier
      extends PythonExecutorBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PythonExecutorMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (PythonExecutorGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PythonExecutorFileDescriptorSupplier())
              .addMethod(getExecuteMethod())
              .addMethod(getHealthCheckMethod())
              .build();
        }
      }
    }
    return result;
  }
}
