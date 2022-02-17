from os import name
import sys
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

class JfrDataPlot:
    """Class for plotting JFR Data"""

    def __init__(self, data) -> None:
        self.frame = data

    def plot_cpu(self):
        self.data.hist(bins=50, figsize=(15,15))
        plt.show()

    def plot_gc(self):
        self.data.hist(bins=50, figsize=(15,15))
        plt.show()

def usage():
    print("""
python plot-jfr-data.py cpu <file>
python plot-jfr-data.py gcTime <file>
""")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        # File handling, and parse the CSV to a frame
        data = pd.read_csv(sys.argv[2])
        data.head()
        plotter = JfrDataPlot(data)
        modes = {'cpu': plotter.plot_cpu, 'gcTime': plotter.plot_gc }

        mode = sys.argv[1]
        if mode in modes:
            modes[mode](*sys.argv[3:])
        else:
            usage()
    else:
        usage()
