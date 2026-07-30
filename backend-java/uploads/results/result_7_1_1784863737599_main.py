import pandas as pd
import re
import os
import sys

# ------------------ 通用提取函数 ------------------
def extract_ci_from_obj(obj_str):
    id_match = re.search(r'(eNodeB|gNodeB)标识=(\d+)', obj_str)
    if not id_match:
        return None
    start_pos = id_match.end()
    cell_match = re.search(r'小区标识=(\d+)', obj_str[start_pos:])
    if cell_match:
        id_value = id_match.group(2)
        cell = cell_match.group(1)
        return f"460-00-{id_value}-{cell}"
    return None

def extract_cell_name_from_obj(obj_str):
    match = re.search(r'小区名称=([^,]+)', obj_str)
    if match:
        return match.group(1).strip()
    match = re.search(r'NR DU小区名称=([^,]+)', obj_str)
    if match:
        return match.group(1).strip()
    return None

# ------------------ 文件读取与识别 ------------------
def read_huawei(filepath):
    try:
        if filepath.endswith('.csv'):
            df_raw = pd.read_csv(filepath, header=None, encoding='utf-8')
        else:
            df_raw = pd.read_excel(filepath, header=None)
    except Exception as e:
        print(f"读取华为文件 {filepath} 失败：{e}")
        return None

    header_row = None
    for idx, row in df_raw.iterrows():
        row_str = ' '.join([str(v) for v in row.values])
        if '对象' in row_str and ('开始时间' in row_str or '干扰' in row_str or 'PRB' in row_str):
            header_row = idx
            break
    if header_row is None:
        return None

    try:
        if filepath.endswith('.csv'):
            df = pd.read_csv(filepath, header=header_row, encoding='utf-8')
        else:
            df = pd.read_excel(filepath, header=header_row)
    except Exception as e:
        print(f"重新读取华为文件失败：{e}")
        return None

    df.columns = df.columns.str.strip()

    inter_col = None
    for col in df.columns:
        if '干扰' in col and 'PRB' in col:
            inter_col = col
            break
    if inter_col is None:
        return None

    time_col = None
    for col in df.columns:
        if '开始时间' in col or '采集时间' in col:
            time_col = col
            break
    if time_col is None:
        return None

    df['CI'] = df['对象'].apply(extract_ci_from_obj)
    df['小区名称'] = df['对象'].apply(extract_cell_name_from_obj)
    df = df.dropna(subset=['CI'])

    df = df[['CI', '小区名称', time_col, inter_col]]
    df.rename(columns={time_col: '时间', inter_col: '干扰值'}, inplace=True)
    return df

def read_zte4g(filepath):
    try:
        if filepath.endswith('.csv'):
            df = pd.read_csv(filepath, encoding='utf-8')
        else:
            df = pd.read_excel(filepath)
    except Exception as e:
        print(f"读取中兴4G文件 {filepath} 失败：{e}")
        return None

    required = ['采集时间', 'eNodeBID', '小区ID', '小区名称', '高干扰小区-sdr-cnop']
    if not all(col in df.columns for col in required):
        if '采集时间' in df.columns and 'eNodeBID' in df.columns and '小区ID' in df.columns and '高干扰小区-sdr-cnop' in df.columns:
            df['小区名称'] = '未知'
        else:
            return None

    df['CI'] = '460-00-' + df['eNodeBID'].astype(str) + '-' + df['小区ID'].astype(str)
    df = df.dropna(subset=['CI'])

    df = df[['采集时间', 'CI', '小区名称', '高干扰小区-sdr-cnop']]
    df.rename(columns={'采集时间': '时间', '高干扰小区-sdr-cnop': '干扰值'}, inplace=True)
    return df

def read_zte5g(filepath):
    try:
        if filepath.endswith('.csv'):
            df = pd.read_csv(filepath, encoding='utf-8')
        else:
            df = pd.read_excel(filepath)
    except Exception as e:
        print(f"读取中兴5G文件 {filepath} 失败：{e}")
        return None

    if 'masterOperatorId' in df.columns and '5G小区RB上行平均干扰电平' in df.columns:
        def fix_cgi(master):
            if isinstance(master, str):
                return master.replace('46000-', '460-00-')
            return None
        df['CI'] = df['masterOperatorId'].apply(fix_cgi)
        df = df.dropna(subset=['CI'])
        df['小区名称'] = '未知'
        df = df[['采集时间', 'CI', '小区名称', '5G小区RB上行平均干扰电平']]
        df.rename(columns={'采集时间': '时间', '5G小区RB上行平均干扰电平': '干扰值'}, inplace=True)
        return df
    else:
        return None

# ------------------ 主程序 ------------------
def main():
    # 获取文件夹路径
    if len(sys.argv) > 1:
        folder = sys.argv[1]
    else:
        folder = input("请输入包含数据文件的文件夹路径：").strip()
        if not folder:
            print("未输入路径，程序退出。")
            return

    if not os.path.isdir(folder):
        print(f"错误：文件夹 '{folder}' 不存在。")
        return

    print(f"正在处理文件夹：{folder}")

    # 获取所有支持的文件
    files = []
    for f in os.listdir(folder):
        if f.endswith(('.xlsx', '.xls', '.csv')):
            files.append(os.path.join(folder, f))

    if not files:
        print("错误：该文件夹中没有找到 .xlsx/.xls/.csv 文件。")
        return

    # 读取并识别文件
    df_school = None
    df_perf_list = []

    for f in files:
        try:
            if f.endswith('.csv'):
                df_tmp = pd.read_csv(f, encoding='utf-8')
            else:
                df_tmp = pd.read_excel(f)
        except Exception as e:
            print(f"读取文件 {os.path.basename(f)} 失败：{e}")
            continue

        # 考场清单识别
        if '学校名称' in df_tmp.columns and 'CGI' in df_tmp.columns:
            if df_school is None:
                df_school = df_tmp
                print(f"识别为考场清单：{os.path.basename(f)}")
            else:
                print(f"警告：已存在考场清单，忽略重复文件 {os.path.basename(f)}")
            continue

        # 性能表识别
        df_h = read_huawei(f)
        if df_h is not None:
            df_perf_list.append(df_h)
            print(f"识别为华为格式性能表：{os.path.basename(f)}")
            continue

        df_z4 = read_zte4g(f)
        if df_z4 is not None:
            df_perf_list.append(df_z4)
            print(f"识别为中兴4G格式性能表：{os.path.basename(f)}")
            continue

        df_z5 = read_zte5g(f)
        if df_z5 is not None:
            df_perf_list.append(df_z5)
            print(f"识别为中兴5G格式性能表：{os.path.basename(f)}")
            continue

        print(f"无法识别文件格式，跳过：{os.path.basename(f)}")

    if df_school is None:
        print("错误：未找到考场清单文件（需包含'学校名称'和'CGI'列）。")
        return

    if not df_perf_list:
        print("错误：未找到任何可用的性能监控数据文件。")
        return

    # 合并性能表
    df_perf = pd.concat(df_perf_list, ignore_index=True)
    df_perf['时间'] = pd.to_datetime(df_perf['时间'], errors='coerce')
    df_perf = df_perf.dropna(subset=['时间'])

    # 按CI取最新时间
    idx_latest = df_perf.groupby('CI')['时间'].idxmax()
    df_perf_latest = df_perf.loc[idx_latest].reset_index(drop=True)

    # 与考场清单匹配
    merged = pd.merge(df_school, df_perf_latest, left_on='CGI', right_on='CI', how='inner')
    if merged.empty:
        print("警告：没有匹配到任何小区，请检查CGI格式。")
        return

    merged['干扰值'] = pd.to_numeric(merged['干扰值'], errors='coerce')

    # 使用“小区中文名”列（如果存在）
    has_cell_chinese = '小区中文名' in merged.columns

    school_status = {}
    school_best_cell = {}

    for school, group in merged.groupby('学校名称'):
        valid = group.dropna(subset=['干扰值'])
        if valid.empty:
            continue
        max_row = valid.loc[valid['干扰值'].idxmax()]
        # 优先使用考场清单中的“小区中文名”
        if has_cell_chinese and pd.notna(max_row.get('小区中文名', None)) and str(max_row.get('小区中文名', '')).strip() != '':
            best_cell = max_row['小区中文名']
        else:
            best_cell = school
        best_val = max_row['干扰值']
        school_best_cell[school] = (best_cell, best_val)

        values = valid['干扰值']
        if any(v >= -100 for v in values):
            school_status[school] = '未关闭'
        elif any(v >= -105 for v in values):
            school_status[school] = '未关全'

    not_closed = [s for s, st in school_status.items() if st == '未关闭']
    not_full   = [s for s, st in school_status.items() if st == '未关全']

    latest_time = df_perf_latest['时间'].max().strftime('%Y-%m-%d %H:%M:%S') if not df_perf_latest.empty else "未知"

    # 构造输出
    output_lines = []
    output_lines.append(f"依据最新监控数据（截至 {latest_time}）：")
    output_lines.append("")
    if not_closed:
        output_lines.append("未关闭：" + "、".join(not_closed))
    else:
        output_lines.append("未关闭：无")
    if not_full:
        output_lines.append("未关全：" + "、".join(not_full))
    else:
        output_lines.append("未关全：无")
    output_lines.append("")

    if not_closed:
        output_lines.append("未关闭详情：")
        for school in not_closed:
            cell, val = school_best_cell.get(school, (None, None))
            if cell is not None:
                output_lines.append(f"  学校：{school}，小区：{cell}，干扰值={val} dBm")
    else:
        output_lines.append("未关闭详情：无")
    output_lines.append("")

    if not_full:
        output_lines.append("未全关详情：")
        for school in not_full:
            cell, val = school_best_cell.get(school, (None, None))
            if cell is not None:
                output_lines.append(f"  学校：{school}，小区：{cell}，干扰值={val} dBm")
    else:
        output_lines.append("未全关详情：无")

    # 输出到控制台
    print("\n" + "\n".join(output_lines))

    # 保存结果文件到当前目录
    output_file = os.path.join(os.getcwd(), "干扰检查结果.txt")
    with open(output_file, "w", encoding="utf-8") as f:
        f.write("\n".join(output_lines))
    print(f"\n结果已保存至：{output_file}")

if __name__ == "__main__":
    main()