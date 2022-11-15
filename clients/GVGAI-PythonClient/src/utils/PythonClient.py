import sys
sys.path.append('../sampleRandom')

from ClientComm import ClientComm


class PythonClient:
    """
     * Python Client given an agent name (SampleRandomAgent by default)
    """

    def __init__(self, args):
        if len(args) > 0:
            agentName = args
        else:
            print("ERROR: Missing argument")
            sys.exit()
        print("start python client")
        ccomm = ClientComm(agentName)
        ccomm.startComm()

if __name__ == "__main__":
    if len(sys.argv) > 0:
        print(f"Run with agent: {str(sys.argv[1])}")
        agentName = str(sys.argv[1])
        lc = PythonClient(f"{agentName}.Agent")
    else:
        lc = PythonClient("Agents.Agent")
