import pandas as pd
import numpy as np
import time

print("=" * 60)
print("复杂Python代码测试")
print("=" * 60)
print()

print("[1] 数据处理测试")
print("-" * 40)
start = time.time()

data = {
    'name': [f'User_{i}' for i in range(1000)],
    'age': np.random.randint(20, 60, 1000),
    'salary': np.random.randint(3000, 20000, 1000),
    'department': np.random.choice(['研发部', '市场部', '财务部', '人事部'], 1000)
}
df = pd.DataFrame(data)

print(f"创建DataFrame完成，共 {len(df)} 行数据")
print(f"列名: {list(df.columns)}")
print()

print("[2] 统计分析测试")
print("-" * 40)
print("基本统计信息:")
print(df.describe())
print()

print("[3] 分组统计")
print("-" * 40)
grouped = df.groupby('department')['salary'].agg(['mean', 'max', 'min', 'count'])
print(grouped.round(2))
print()

print("[4] 数据筛选")
print("-" * 40)
high_salary = df[df['salary'] > 15000]
print(f"高薪员工（薪资>15000）: {len(high_salary)} 人")
print(high_salary[['name', 'age', 'salary', 'department']].head(10))
print()

print("[5] 耗时计算")
print("-" * 40)
elapsed = time.time() - start
print(f"总执行时间: {elapsed:.2f} 秒")
print()

print("[6] 复杂计算测试")
print("-" * 40)
result = 0
for i in range(1, 1000001):
    result += i * i
print(f"1到100万的平方和: {result}")
print()

print("=" * 60)
print("测试完成！")
print("=" * 60)