package com.cqie.datafactory.executor.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.cqie.datafactory.executor.entity.NodeFieldValue;
import com.cqie.datafactory.executor.mapper.NodeFieldValueMapper;
import com.cqie.datafactory.executor.service.NodeFieldValueService;
import org.springframework.stereotype.Service;

@Service
public class NodeFieldValueServiceImpl extends ServiceImpl<NodeFieldValueMapper, NodeFieldValue> implements NodeFieldValueService {
}
