package me.akika.githive.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.akika.githive.auth.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppUserMapper extends BaseMapper<AppUser> {
}
