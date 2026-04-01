package me.akika.githive.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import me.akika.githive.auth.entity.UserRefreshToken;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRefreshTokenMapper extends BaseMapper<UserRefreshToken> {
}
