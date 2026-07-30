package com.toolplatform;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.toolplatform.entity.*;
import com.toolplatform.repository.*;

@SpringBootApplication
public class ToolPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolPlatformApplication.class, args);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CommandLineRunner initData(UserRepository userRepo, ToolRepository toolRepo,
                                       MessageRepository msgRepo, DownloadStatRepository statRepo,
                                       PasswordEncoder encoder) {
        return args -> {
            if (userRepo.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPasswordHash(encoder.encode("123456"));
                admin.setNickname("管理员");
                admin.setRole("admin");
                admin.setCompany("平台管理");
                admin.setDepartment("技术部");
                admin.setBio("系统管理员");
                admin.setEmail("admin@example.com");
                admin.setPhone("13800000000");
                userRepo.save(admin);

                Object[][] tools = {
                    {"需求分析与原型设计模板", "excel", "规划", "需求,原型,PRD", "一个标准化的Excel模板，用于快速编写产品需求文档和绘制低保真原型。", "产品部", "产品部-张三", 1258},
                    {"自动化部署脚本", "python", "建设", "CI/CD,部署,python", "用于自动化部署Web应用的Python脚本，支持多种环境配置。", "运维部", "运维部-李四", 980},
                    {"数据库巡检工具", "bash", "维护", "数据库,监控,bash", "定时巡检数据库健康状况，并生成报告的Bash脚本。", "技术中台", "DBA-王五", 760},
                    {"前端性能分析报告", "excel", "优化", "性能,前端,FCP", "填写性能指标，自动生成可视化分析报告和优化建议。", "研发部", "前端组-赵六", 1520},
                    {"用户活动数据提取器", "python", "客服", "数据,用户行为,SQL", "连接数据仓库，根据配置提取用户活动数据并导出为CSV文件。", "数据分析部", "数据组-孙七", 2130},
                    {"项目周报生成器", "excel", "规划", "周报,项目管理", "输入本周工作内容，自动格式化并生成项目周报。", "PMO", "项目管理-周八", 850},
                    {"代码规范检查工具", "python", "建设", "lint,代码质量", "集成了多种Linter的代码规范自动化检查脚本。", "技术中台", "架构组-吴九", 640},
                };
                for (Object[] t : tools) {
                    Tool tool = new Tool();
                    tool.setName((String) t[0]);
                    tool.setType((String) t[1]);
                    tool.setCategory((String) t[2]);
                    tool.setKeywords((String) t[3]);
                    tool.setDescription((String) t[4]);
                    tool.setDepartment((String) t[5]);
                    tool.setAuthorName((String) t[6]);
                    tool.setDownloads((int) t[7]);
                    tool.setAuthorId(admin.getId());
                    tool.setStatus("online");
                    toolRepo.save(tool);
                }

                Object[][] msgs = {
                    {"问题反馈", "需求分析模板无法打开", "打开时报错说文件损坏", "张三"},
                    {"问题答复", "Re: 部署脚本报错", "已修复该问题，请重新下载", "李四"},
                    {"系统消息", "平台将于今晚进行升级维护", "预计维护时间2小时", "系统管理员"},
                };
                for (Object[] m : msgs) {
                    Message msg = new Message();
                    msg.setType((String) m[0]);
                    msg.setTitle((String) m[1]);
                    msg.setContent((String) m[2]);
                    msg.setFromUser((String) m[3]);
                    msg.setToUserId(admin.getId());
                    msg.setStatus("未读");
                    msgRepo.save(msg);
                }

                String[] dates = {"2026-07-01", "2026-07-05", "2026-07-10", "2026-07-15", "2026-07-20", "2026-07-22"};
                var allTools = toolRepo.findAll();
                for (Tool tool : allTools) {
                    for (String d : dates) {
                        DownloadStat stat = new DownloadStat();
                        stat.setToolId(tool.getId());
                        stat.setDate(d);
                        stat.setCount((int)(Math.random() * 30) + 5);
                        statRepo.save(stat);
                    }
                }
                System.out.println("Initial data created successfully!");
            }
        };
    }
}
