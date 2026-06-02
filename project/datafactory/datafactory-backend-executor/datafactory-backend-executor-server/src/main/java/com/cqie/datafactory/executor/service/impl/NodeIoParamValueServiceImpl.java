package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.entity.NodeIoParamValue;
import com.cqie.datafactory.executor.mapper.NodeIoParamValueMapper;
import com.cqie.datafactory.executor.service.NodeIoParamValueService;
import org.springframework.stereotype.Service;

@Service
public class NodeIoParamValueServiceImpl extends ServiceImpl<NodeIoParamValueMapper, NodeIoParamValue> implements NodeIoParamValueService {
}
