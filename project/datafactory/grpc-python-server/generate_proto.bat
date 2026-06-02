python -m grpc_tools.protoc -I. --python_out=. --grpc_python_out=. executor.proto
echo Proto stubs generated: executor_pb2.py, executor_pb2_grpc.py
