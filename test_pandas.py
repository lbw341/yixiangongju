import pandas as pd

data = {'Name': ['Alice', 'Bob', 'Charlie', 'David'],
        'Age': [25, 30, 35, 40],
        'Salary': [5000, 6000, 7500, 8000]}

df = pd.DataFrame(data)
print("DataFrame:")
print(df)
print("\n统计信息:")
print(df.describe())
print("\n姓名列表:")
print(df['Name'].tolist())