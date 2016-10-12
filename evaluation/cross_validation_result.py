import pandas as pd
import numpy as np
from matplotlib import pyplot as plt


import compare_y



def main():
    train_file = "../psl/data/first_model/seed0/cross_val/5fold/fold{0}_train.txt"
    test_file = "../psl/data/first_model/seed0/cross_val/5fold/fold{0}_val.txt"
    infer_file = "../psl/result/first_model_cross_val_fold{0}_result{1}.txt" 
    rows = []
    for fold in range(1, 6):
        tr_df, val_df, infer_df = compare_y.load_data(train_file.format(fold),
                                                      test_file.format(fold),
                                                      infer_file.format(fold, ""))
        tr_df, val_df, infer_df_active = compare_y.load_data(train_file.format(fold),
                                                             test_file.format(fold),
                                                             infer_file.format(fold, "_activeRule"))
        tr_df, val_df, infer_df_essen = compare_y.load_data(train_file.format(fold),
                                                            test_file.format(fold),
                                                            infer_file.format(fold, "_essentialRule"))
        tr_both_rules, _, _ = compare_y.calculate_accuracy(tr_df, infer_df)
        test_both_rules, _, _ = compare_y.calculate_accuracy(val_df, infer_df)
        tr_active_rule, _, _ = compare_y.calculate_accuracy(tr_df, infer_df_active)
        test_active_rule, _, _ = compare_y.calculate_accuracy(val_df, infer_df_active)
        tr_essen_rule, _, _ = compare_y.calculate_accuracy(tr_df, infer_df_essen)
        test_essen_rule, _, _ = compare_y.calculate_accuracy(val_df, infer_df_essen)
        rows.append({"train_both_rules": tr_both_rules, "test_both_rules": test_both_rules,
                     "train_active_rule": tr_active_rule, "test_active_rule": test_active_rule,
                     "train_essential_rule": tr_essen_rule, "test_essential_rule": test_essen_rule})
    df = pd.DataFrame(rows)
    df = df[["train_both_rules", "test_both_rules", "train_active_rule", "test_active_rule",
             "train_essential_rule", "test_essential_rule"]]

    plotting(df)





def plotting(df):
    mean = df.mean()
    std = df.std()
    nice_mean = prettifying_df_for_bar_plot(mean)
    nice_std = prettifying_df_for_bar_plot(std)
    nice_mean.plot.bar(yerr=nice_std, rot=30, alpha=0.75)
    plt.ylabel("Mean Squared Error")
    plt.show()


def prettifying_df_for_bar_plot(df):
    nice_df = pd.DataFrame(index=["both_rules", "active_rule", "essential_rule"],
                           columns = ["train", "test"])
    for name in df.index:
        exp_type = name.split("_")[0]
        rest = "_".join(name.split("_")[1:])
        nice_df.loc[rest][exp_type] = df[name]

    return nice_df 
    




if __name__=="__main__":
    main()