#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================"
echo "  Python gRPC Executor - 一键启动"
echo "============================================"
echo ""

# ── 1. 检查 Python ──
echo "[1/3] 检查 Python 环境..."
if ! command -v python3 &>/dev/null && ! command -v python &>/dev/null; then
    echo "[ERROR] 未找到 Python，请先安装 Python 3.8+"
    exit 1
fi
PYTHON=$(command -v python3 || command -v python)
$PYTHON --version
echo ""

# ── 2. 安装依赖 ──
echo "[2/3] 检查并安装依赖..."
if ! $PYTHON -c "import grpc" 2>/dev/null; then
    echo "       正在安装 grpcio grpcio-tools..."
    pip install -r requirements.txt --quiet 2>/dev/null || \
    pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple --quiet
fi
echo "       依赖就绪: grpcio, grpcio-tools"
echo ""

# ── 3. 启动服务 ──
echo "[3/3] 启动 Python gRPC 服务 (端口: 50051)..."
echo "============================================"
echo "  服务地址: 127.0.0.1:50051"
echo "  健康检查: gRPC HealthCheck"
echo "  按 Ctrl+C 停止服务"
echo "============================================"
echo ""

$PYTHON server.py --port 50051
