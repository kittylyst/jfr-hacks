import sys
import re
import pandas as pd
import matplotlib.pyplot as plt
#import seaborn as sns

class JfrDataPlot:
    """Class for plotting JFR Data"""

    @staticmethod
    def stem_filename(fname):
        return re.sub(r"\.csv$", "", fname)

    def __init__(self, files) -> None:
        if isinstance(files, list):
            self.files = files
        else:
            raise ValueError("Was not passed a list")

        self.data = []
        for fname in files:
            data = pd.read_csv(fname)
            data.head()
            self.data.append(data)

    """timestamp,user,system,total"""
    def plot_cpu(self, stem):
        self.data.plot(x="timestamp", y=["user","system","total"])
        image_name = stem + '_cpu.png'
        plt.savefig(image_name)

    # Bash snippet for determining whether the GC output for duration is STW
    # Stupid grep trick to work around cat apparently not having the ability to print the name of the
    # file before the line
    # grep -H -E -e '' *_data/gc*.csv | sort -n --field-separator=',' --key=2

    def biplot_gc_total(self, stem, stem2):
        ax = self.data.plot(x="timestamp", y=["totalPause"], title='GC Total Pause per Collection', ylabel='millis')
        self.data2.plot(ax=ax, x="timestamp", y=["totalPause"], title='GC Total Pauseper Collection', ylabel='millis')
        ax.legend([stem, stem2])
        # plt.show()
        image_name = stem + '_'+ stem2 +'_total_pause.png'

    def plot_gc(self, stems):
        stem_base = '_'.join(stems)

        for idx, data in enumerate(self.data):
            if idx == 0:
                ax = data.plot(x="timestamp", y=["elapsedMs"], title='GC Elapsed Time', ylabel='millis')
            else:
                ax = data.plot(ax=ax, x="timestamp", y=["elapsedMs"], title='GC Elapsed Time', ylabel='millis')
        ax.legend(stems)
        image_name = stem_base + '_elapsed.png'
        plt.savefig(image_name)

        for idx, data in enumerate(self.data):
            if idx == 0:
                ax = data.plot(x="timestamp", y=["longestPause"], title='GC Longest Pause per Collection', ylabel='millis')
            else:
                ax = data.plot(ax=ax, x="timestamp", y=["longestPause"], title='GC Longest Pause per Collection', ylabel='millis')
        ax.legend(stems)
        image_name = stem_base + '_longest.png'
        plt.savefig(image_name)

        for idx, data in enumerate(self.data):
            if idx == 0:
                ax = data.plot(x="timestamp", y=["totalPause"], title='GC Total Pause per Collection', ylabel='millis')
            else:
                ax = data.plot(ax=ax, x="timestamp", y=["totalPause"], title='GC Total Pause per Collection', ylabel='millis')
        ax.legend(stems)
        image_name = stem_base + '_total_pause.png'
        plt.savefig(image_name)

        for idx, data in enumerate(self.data):
            if idx == 0:
                ax = data.plot(x="timestamp", y=["heapUsedAfter"], title='Heap Used After Collection', ylabel='bytes')
            else:
                ax = data.plot(ax=ax, x="timestamp", y=["heapUsedAfter"], title='Heap Used After Collection', ylabel='bytes')
        ax.legend(stems)
        image_name = stem_base + '_heap_after.png'
        plt.savefig(image_name)

        for idx, data in enumerate(self.data):
            data["cpu_cum_sum"] = data["cpuUsedMs"].cumsum()
            if idx == 0:
                ax = data.plot(x="timestamp", y=["cpu_cum_sum"], title='GC Cumulative Time', ylabel='millis')
            else:
                ax = data.plot(ax=ax, x="timestamp", y=["cpu_cum_sum"], title='GC Cumulative Time', ylabel='millis')
        ax.legend(stems)
        image_name = stem_base + '_cpu_cumulative.png'
        plt.savefig(image_name)

def usage():
    print("""
python plot-jfr-data.py cpu <file>
python plot-jfr-data.py gc <file>+
""")

if __name__ == "__main__":
    if len(sys.argv) > 2:
        # File handling, and parse the CSV to a frame
        mode = sys.argv[1]
        plotter = JfrDataPlot(sys.argv[2:])
        modes = {'cpu': plotter.plot_cpu, 'gc': plotter.plot_gc }
        stems = list(map(JfrDataPlot.stem_filename, sys.argv[2:]))
        if mode in modes:
            modes[mode](stems)
        else:
            usage()
    else:
        usage()

