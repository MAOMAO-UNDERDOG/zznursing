package com.zzyl.nursing.service.impl;

import java.util.Arrays;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zzyl.common.utils.DateUtils;
import com.zzyl.nursing.vo.NursingLevelVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.zzyl.nursing.mapper.NursingLevelMapper;
import com.zzyl.nursing.domain.NursingLevel;
import com.zzyl.nursing.service.INursingLevelService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import static com.zzyl.common.constant.CacheConstants.CACHE_LEVEL_ALL_KEY;


/**
 * 护理等级Service业务层处理
 * 
 * @author alexis
 * @date 2025-06-02
 */
@Service
public class NursingLevelServiceImpl extends ServiceImpl<NursingLevelMapper, NursingLevel> implements INursingLevelService
{
    @Autowired
    private NursingLevelMapper nursingLevelMapper;

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;


    /**
     * 查询护理等级
     *
     * @param id 护理等级主键
     * @return 护理等级
     */
    @Override
    public NursingLevel selectNursingLevelById(Long id)
    {
        return nursingLevelMapper.selectNursingLevelById(id);
    }

    /**
     * 查询护理等级列表
     *
     * @param nursingLevel 护理等级
     * @return 护理等级
     */
    @Override
    public List<NursingLevel> selectNursingLevelList(NursingLevel nursingLevel) {
        return nursingLevelMapper.selectNursingLevelList(nursingLevel);
    }

    /**
     * 查询护理等级Vo列表
     *
     * @param nursingLevel 护理等级
     * @return 护理等级
     */
    @Override
    public List<NursingLevelVo> selectNursingLevelVoList(NursingLevel nursingLevel)
    {
        return nursingLevelMapper.selectNursingLevelVoList(nursingLevel);
    }

    /**
     * 新增护理等级
     *
     * @param nursingLevel 护理等级
     * @return 结果
     */
    @Override
    public int insertNursingLevel(NursingLevel nursingLevel)
    {
        nursingLevel.setCreateTime(DateUtils.getNowDate());
        int flag = nursingLevelMapper.insertNursingLevel(nursingLevel);
        // 删除缓存
        deleteCache();
        return flag;
    }

    private void deleteCache() {
        // 删除缓存
        redisTemplate.delete(CACHE_LEVEL_ALL_KEY);
    }

    /**
     * 修改护理等级
     *
     * @param nursingLevel 护理等级
     * @return 结果
     */
    @Override
    public int updateNursingLevel(NursingLevel nursingLevel)
    {
        nursingLevel.setUpdateTime(DateUtils.getNowDate());
        int flag =  nursingLevelMapper.updateNursingLevel(nursingLevel);
        deleteCache();
        return flag;
    }

    /**
     * 批量删除护理等级
     *
     * @param ids 需要删除的护理等级主键
     * @return 结果
     */
    @Override
    public int deleteNursingLevelByIds(Long[] ids)
    {
        int flag = nursingLevelMapper.deleteNursingLevelByIds(ids);
        deleteCache();
        return flag;
    }

    /**
     * 删除护理等级信息
     *
     * @param id 护理等级主键
     * @return 结果
     */
    @Override
    public int deleteNursingLevelById(Long id)
    {
        int flag = nursingLevelMapper.deleteNursingLevelById(id);
        deleteCache();
        return flag;
    }

    /**
     * 查询所有护理等级
     * @return
     */
    @Override
    public List<NursingLevel> listAll() {

        // 从缓存中获取
        List<NursingLevel> list = (List<NursingLevel>) redisTemplate.opsForValue().get(CACHE_LEVEL_ALL_KEY);
        // 缓存中有数据，直接返回
        if(list != null && list.size() > 0){
            return list;
        }
        // 缓存中没有数据，从数据库中查询
        LambdaQueryWrapper<NursingLevel> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(NursingLevel::getStatus, 1);
        list = list(queryWrapper);
        // 将数据写入缓存
        redisTemplate.opsForValue().set(CACHE_LEVEL_ALL_KEY, list);

        return list;
    }
}
