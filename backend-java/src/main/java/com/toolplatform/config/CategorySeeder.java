package com.toolplatform.config;

import com.toolplatform.entity.Category;
import com.toolplatform.repository.CategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 分类种子数据初始化
 * 只有当 categories 表为空时才插入，支持 Flyway 迁移或 JPA ddl-auto=update 两种场景
 */
@Component
public class CategorySeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepo;

    public CategorySeeder(CategoryRepository categoryRepo) {
        this.categoryRepo = categoryRepo;
    }

    @Override
    public void run(String... args) {
        if (categoryRepo.count() > 0) return; // 已有数据就不插了

        Object[][] seeds = {
            {"规划", "fas fa-compass",    "indigo",  1},
            {"建设", "fas fa-hammer",     "emerald", 2},
            {"优化", "fas fa-chart-line", "amber",   3},
            {"维护", "fas fa-wrench",     "sky",     4},
            {"客服", "fas fa-headset",    "rose",    5}
        };
        for (Object[] s : seeds) {
            Category c = new Category();
            c.setName((String) s[0]);
            c.setIcon((String) s[1]);
            c.setColor((String) s[2]);
            c.setSortOrder((Integer) s[3]);
            categoryRepo.save(c);
        }
        System.out.println("[CategorySeeder] 插入 " + seeds.length + " 个默认分类");
    }
}
