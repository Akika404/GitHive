package me.akika.githive.namespace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.akika.githive.namespace.entity.Namespace;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NamespaceMapper extends BaseMapper<Namespace> {
}
