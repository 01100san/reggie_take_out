package com.mysite.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mysite.reggie.dto.SetmealDto;
import com.mysite.reggie.entity.Category;
import com.mysite.reggie.entity.Dish;
import com.mysite.reggie.entity.Setmeal;
import com.mysite.reggie.entity.SetmealDish;
import com.mysite.reggie.exception.SetmealStatusException;
import com.mysite.reggie.mapper.DishMapper;
import com.mysite.reggie.mapper.SetmealMapper;
import com.mysite.reggie.service.CategoryService;
import com.mysite.reggie.service.SetmealDishService;
import com.mysite.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ClassName: SetmealServiceImpl
 * Package: com.mysite.reggie.service.impl
 * Description
 *
 * @Author zhl
 * @Create 2023/11/25 12:36
 * version 1.0
 */
@Slf4j
@Service
public class SetmealServiceImpl extends ServiceImpl<SetmealMapper, Setmeal> implements SetmealService {
    @Autowired
    private SetmealDishService setmealDishService;
    @Autowired
    private CategoryService categoryService;

    @Override
    public Page<SetmealDto> pageSetmealDto(Integer currentPage, Integer pageSize, String name) {
        Page<Setmeal> setmealPage = new Page<>(currentPage,pageSize);
        //先声明一个Page<SetmealDto>的变量，可以不赋currentPage和pageSize值，之后会把Page<Setmeal>的属性值赋给setmealDtoPage
        Page<SetmealDto> setmealDtoPage = new Page<>();

        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotEmpty(name),Setmeal::getName,name)
                .orderByAsc(Setmeal::getUpdateTime);
        //分页查询查找到了setmeal对应的值setmeals。
        this.page(setmealPage,queryWrapper);

        //不能直接将setmeals的记录值直接赋给 records，我先将currentPage和pageSize值赋给re
        BeanUtils.copyProperties(setmealPage,setmealDtoPage,"records");

        List<Setmeal> setmeals = setmealPage.getRecords();

        //根据categoryId设置categoryName
        List<SetmealDto> records = setmeals.stream().map((Setmeal setmeal) -> {
            SetmealDto setmealDto = new SetmealDto();
            //把setmeal的属性赋给setmealDto
            BeanUtils.copyProperties(setmeal,setmealDto);
            Long categoryId = setmealDto.getCategoryId();
            Category category = categoryService.getById(categoryId);
            if (category != null){
                setmealDto.setCategoryName(category.getName());
            }
            return setmealDto;
        }).collect(Collectors.toList());

        //为setmealDtoPage中的records赋值
        setmealDtoPage.setRecords(records);
        //前端页面想要展示categoryName套餐分类的值，但是在setmeal中只有categoryId值。
        //通过为setmealDto中的categoryName赋值即可得到
        //最后返回给前端的数据是setmealDtoPage,即setmealDto对象,所以要给setmealDtoPage的categoryName赋值，为records集合赋值
        return setmealDtoPage;
    }

    @Override
    public List<Setmeal> listSetmeals(Long categoryId, Integer status) {
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(status != null,Setmeal::getStatus,1)
                .eq(Setmeal::getCategoryId,categoryId);
        return this.list(queryWrapper);
    }

    /**
     * 在category表中，是菜品种类和套餐种类的分类表，setmeal是针对某种套餐种类categoryId的具体套餐
     * 修改setmeal套餐表 =》直接修改
     * 修改setmeal_dish表 =》获取setmealDto.getSetmealDishes()，
     *      为setmeal_dish表赋值,为setmeal_id赋值
     * @param setmealDto
     * @return
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDto setmealDto) {
        //修改setmeal套餐表 =》直接修改
        super.save(setmealDto);
        Long setmealId = setmealDto.getId();
        //获取套餐中添加的菜品列表 setmealDishes
        List<SetmealDish> setmealDishes = setmealDto.getSetmealDishes();
        //循环遍历 将setmealDishes存入setmeal_dish表中
        for (SetmealDish setmealDish : setmealDishes){
            //为setmeal_dish表中的setmeal_id赋值
            setmealDish.setSetmealId(setmealId);
        }
        //使用批量插入的方式，减少与数据库交互的次数
        setmealDishService.saveBatch(setmealDishes);
    }

    /**
     * 根据id查询 setmeal
     * 先查询setmeal
     * 后查询setmeal_dish
     * @param id
     * @return
     */
    @Override
    public SetmealDto getByIdWithDishes(Long id) {
        //根据id查询得到对应的setmeal信息
        Setmeal setmeal = this.getById(id);
        SetmealDto setmealDto = new SetmealDto();
        //将setmeal为setmealDto的属性赋值
        BeanUtils.copyProperties(setmeal,setmealDto);
        //根据setmealId查询setmeal_dish表中对应的菜品列表
        LambdaQueryWrapper<SetmealDish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealDish::getSetmealId,id);
        List<SetmealDish> dishes = setmealDishService.list(queryWrapper);
        //为setmealDto的setmealDishes属性赋值
        setmealDto.setSetmealDishes(dishes);

        return setmealDto;
    }

    /**
     * 修改套餐
     * 更新setmeal表，更新setmeal_dish表
     * @param setmealDto
     */
    @Transactional
    @Override
    public void updateByIdWithDishes(SetmealDto setmealDto) {
        //根据setmealId更新setmeal表
        this.updateById(setmealDto);
        //获取setmealId
        Long setmealId = setmealDto.getId();
        //更新setmeal_dish表
        //先获取setmealDishes
        List<SetmealDish> setmealDishes = setmealDto.getSetmealDishes();
        /*
            copies: 1
            dishId: "1413385247889891330"
            name: "米饭"
            price: 600
         */
        //建议先remove再save
        LambdaQueryWrapper<SetmealDish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealDish::getSetmealId,setmealId);
        setmealDishService.remove(queryWrapper);
        for (SetmealDish setmealDish : setmealDishes){
            //遍历，更新setmealDish表中的数据
            setmealDish.setSetmealId(setmealId);
            log.info("setmealDish：{}",setmealDish);
        }
        //删除后批量插入setmealDishes
        setmealDishService.saveBatch(setmealDishes);
    }

    /**
     * 删除操作，根据ids删除setmeal中的setmeal
     * 根据ids删除setmeal_dish中的dish
     * 删除时，先判断套餐是否正在售卖，如果正在售卖，不能删除
     * @param ids setmeal_id
     */
    @Override
    @Transactional
    public void removeByIdWithDishes(List<Long> ids) {
        LambdaQueryWrapper<Setmeal> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.in(Setmeal::getId,ids)
                .eq(Setmeal::getStatus,1);
        int count = this.count(lambdaQueryWrapper);
        //要删除的ids 的status=1抛异常
        if (count > 0){
            throw new SetmealStatusException("套餐正在售卖中，不能删除");
        }
        //可以删除
        //1.根据ids删除setmeal
        this.removeByIds(ids);
        //2.根据setmeal_id=ids删除setmeal_dish中的数据
        LambdaQueryWrapper<SetmealDish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SetmealDish::getSetmealId,ids);
        setmealDishService.remove(queryWrapper);
    }

    @Override
    public void updateByIdWithStatus(Integer status, List<Long> ids) {
        UpdateWrapper<Setmeal> updateWrapper = new UpdateWrapper<>();
        updateWrapper.set("status",status).in("id",ids);
        this.update(updateWrapper);
    }
}
