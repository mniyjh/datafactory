"""Python Executor gRPC Server — 类方法调用模式.

特性:
  - importlib 动态加载脚本模块，无需 subprocess
  - 根据 className/methodName 实例化类并调用方法
  - 方法参数(input_params)作为 kwargs 传入
  - 返回 dict 序列化为 result_json
  - 自动注册到 Nacos 服务发现

启动:
  pip install -r requirements.txt
  python server.py --port 50051
"""

import sys
import os
import json
import time
import tempfile
import argparse
import traceback
import importlib.util
import subprocess
from concurrent import futures

import grpc
import executor_pb2
import executor_pb2_grpc


class PythonExecutorServicer(executor_pb2_grpc.PythonExecutorServicer):
    """gRPC 服务实现: 接收脚本内容+类名+方法名+参数，执行并返回结果."""

    def Execute(self, request, context):
        start_time = time.time()
        script_content = request.script_content
        class_name = request.class_name or "Script"
        method_name = request.method_name or "execute"
        timeout = request.timeout_seconds if request.timeout_seconds else 30
        input_params = dict(request.input_params)

        script_path = None
        module_name = f"exec_script_{int(start_time)}"

        try:
            # 安装脚本依赖（通过 _DF_DEPENDENCIES 环境变量传入）
            deps_json = input_params.pop("_DF_DEPENDENCIES", None)
            if deps_json:
                try:
                    deps = json.loads(deps_json)
                    if isinstance(deps, list) and deps:
                        print(f"[DF] Installing dependencies: {deps}", file=sys.stderr)
                        subprocess.check_call(
                            [sys.executable, "-m", "pip", "install", "--quiet"] + deps,
                            timeout=300,
                        )
                except (json.JSONDecodeError, subprocess.CalledProcessError) as e:
                    print(f"[DF] Dependency install failed: {e}", file=sys.stderr)

            # 将脚本写入临时文件
            with tempfile.NamedTemporaryFile(
                mode="w", suffix=".py", delete=False, encoding="utf-8"
            ) as f:
                f.write(script_content)
                script_path = f.name

            # 动态加载模块
            spec = importlib.util.spec_from_file_location(module_name, script_path)
            module = importlib.util.module_from_spec(spec)
            sys.modules[module_name] = module
            spec.loader.exec_module(module)

            # 取类 → 实例化 → 调方法
            cls = getattr(module, class_name)
            instance = cls()
            method = getattr(instance, method_name)

            # 类型转换：gRPC map<string,string> 的值全是字符串，转为合适类型
            converted_params = _convert_params(input_params)

            # 带超时调用
            with futures.ThreadPoolExecutor(max_workers=1) as pool:
                future = pool.submit(method, **converted_params)
                result = future.result(timeout=timeout)

            if not isinstance(result, dict):
                result = {"result": result}

            result_json = json.dumps(result, ensure_ascii=False, default=str)
            duration_ms = int((time.time() - start_time) * 1000)

            return executor_pb2.ExecuteResponse(
                exit_code=0,
                stdout=result_json,
                result_json=result_json,
                duration_ms=duration_ms,
            )

        except futures.TimeoutError:
            duration_ms = int((time.time() - start_time) * 1000)
            return executor_pb2.ExecuteResponse(
                exit_code=-1,
                stderr=f"执行超时({timeout}s)",
                error_message="TIMEOUT",
                duration_ms=duration_ms,
            )
        except Exception as e:
            duration_ms = int((time.time() - start_time) * 1000)
            return executor_pb2.ExecuteResponse(
                exit_code=-1,
                stderr=traceback.format_exc(),
                error_message=str(e),
                duration_ms=duration_ms,
            )
        finally:
            if script_path:
                try:
                    os.unlink(script_path)
                except OSError:
                    pass
            if module_name in sys.modules:
                del sys.modules[module_name]

    def HealthCheck(self, request, context):
        return executor_pb2.HealthCheckResponse(
            healthy=True,
            python_version=sys.version,
            message="Python Executor gRPC server running",
        )


def _convert_params(params):
    """将 map<string,string> 转为合适类型: 数字→int/float, 'true'/'false'→bool."""
    result = {}
    for k, v in params.items():
        if v is None:
            result[k] = None
            continue
        # try int → float → bool → str
        try:
            result[k] = int(v)
        except (ValueError, TypeError):
            try:
                result[k] = float(v)
            except (ValueError, TypeError):
                if v.lower() in ("true", "false"):
                    result[k] = v.lower() == "true"
                else:
                    result[k] = v
    return result


def serve(port=50051):
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    executor_pb2_grpc.add_PythonExecutorServicer_to_server(
        PythonExecutorServicer(), server
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"gRPC Python Executor listening on port {port}")

    try:
        server.wait_for_termination()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.stop(0)
        server.stop(0)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Python Executor gRPC Server")
    parser.add_argument("--port", type=int, default=50051, help="gRPC port")
    args = parser.parse_args()
    serve(args.port)
