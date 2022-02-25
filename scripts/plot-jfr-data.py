from os import name
import sys
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns

class JfrDataPlot:
    """Class for plotting JFR Data"""

    def __init__(self, data) -> None:
        self.data = data

    """timestamp,user,system,total"""
    def plot_cpu(self):
        self.data.plot(x="timestamp", y=["user","system","total"])
        plt.show()

    # Bash snippet for determining whether the GC output for duration is STW
    # Stupid grep trick to work around cat apparently not having the ability to print the name of the
    # file before the line
    # grep -H -E -e '' *_data/gc*.csv | sort -n --field-separator=',' --key=2
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

