from os import name
import sys
import re
import pandas as pd
import matplotlib.pyplot as plt
#import seaborn as sns

class JfrDataPlot:
    """Class for plotting JFR Data"""

    def __init__(self, data) -> None:
        self.data = data

    def __init__(self, data, data2) -> None:
        self.data = data
        self.data2 = data2

    @staticmethod
    def stem_filename(fname):
        return re.sub(r"\.csv$", "", fname)

    """timestamp,user,system,total"""
    def plot_cpu(self, stem):
        self.data.plot(x="timestamp", y=["user","system","total"])
        image_name = stem + '.png'
        plt.savefig(image_name)

    def show_cpu(self, stem):
        self.data.plot(x="timestamp", y=["user","system","total"])
        plt.show()

    # Bash snippet for determining whether the GC output for duration is STW
    # Stupid grep trick to work around cat apparently not having the ability to print the name of the
    # file before the line
    # grep -H -E -e '' *_data/gc*.csv | sort -n --field-separator=',' --key=2

    # New CSV format for GC from Analysis class
    # timestamp,gcId,elapsedMs,cpuUsedMs,totalPause,longestPause,heapUsedAfter
    def plot_gc(self, stem):
        self.data.plot(x="timestamp", y=["elapsedMs"])
        image_name = stem + '_elapsed.png'
        plt.savefig(image_name)
        self.data.plot(x="timestamp", y=["longestPause"])
        image_name = stem + '_longest.png'
        plt.savefig(image_name)
        self.data.plot(x="timestamp", y=["heapUsedAfter"])
        image_name = stem + '_heap_after.png'
        plt.savefig(image_name)

        # timestamp,gcId,elapsedMs,cpuUsedMs,totalPause,longestPause,heapUsedAfter
    def biplot_gc(self, stem, stem2):
        ax = self.data.plot(x="timestamp", y=["elapsedMs"])
        self.data2.plot(ax=ax, x="timestamp", y=["elapsedMs"])
        ax.legend([stem, stem2])
        # plt.show()
        image_name = stem + '_'+ stem2 +'_elapsed.png'
        plt.savefig(image_name)

    def biplot_gc_cum(self, stem, stem2):
        self.data["cpu_cum_sum"] = self.data["cpuUsedMs"].cumsum()
        self.data2["cpu_cum_sum"] = self.data2["cpuUsedMs"].cumsum()
        ax = self.data.plot(x="timestamp", y=["cpu_cum_sum"])
        self.data2.plot(ax=ax, x="timestamp", y=["cpu_cum_sum"])
        ax.legend([stem, stem2])
        # plt.show()
        image_name = stem + '_'+ stem2 +'_cpu_cumulative.png'
        plt.savefig(image_name)


def usage():
    print("""
python plot-jfr-data.py cpu <file>
python plot-jfr-data.py cpu_show <file>
python plot-jfr-data.py gc_time <file>
python plot-jfr-data.py gc_bi_plot <file1> <file2>
python plot-jfr-data.py gc_cumulative <file1> <file2>
""")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        mode = sys.argv[1]
        fname = sys.argv[2]
        stem = JfrDataPlot.stem_filename(fname)
        data = pd.read_csv(fname)
        data.head()

        # FIXME Handle the multiple plots in a better way
        if mode == 'gc_bi_plot':
            fname2 = sys.argv[3]
            stem2 = JfrDataPlot.stem_filename(fname2)
            data2 = pd.read_csv(fname2)
            data2.head()
            plotter = JfrDataPlot(data, data2)
            plotter.biplot_gc(stem, stem2)
        elif mode == 'gc_cumulative':
            fname2 = sys.argv[3]
            stem2 = JfrDataPlot.stem_filename(fname2)
            data2 = pd.read_csv(fname2)
            data2.head()
            plotter = JfrDataPlot(data, data2)
            plotter.biplot_gc_cum(stem, stem2)
        else:
            # File handling, and parse the CSV to a frame
            plotter = JfrDataPlot(data)
            modes = {'cpu': plotter.plot_cpu, 'cpu_show': plotter.show_cpu, 'gc_time': plotter.plot_gc }
            if mode in modes:
                modes[mode](stem, *sys.argv[3:])
            else:
                usage()
    else:
        usage()

# jdk.GCHeapSummary {
#   startTime = 14:42:44.690
#   gcId = 4
#   when = "Before GC"
#   heapSpace = {
#     start = 0xC0000000
#     committedEnd = 0xC2200000
#     committedSize = 34.0 MB
#     reservedEnd = 0x100000000
#     reservedSize = 1.0 GB
#   }
#   heapUsed = 20.5 MB
# }

