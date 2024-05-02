import os
import argparse

def convert_file(src: str, dst: str):
    print(src)
    with open(src, "r") as s:
        with open(dst, "w") as d:
            count = 0
            deltaT = None
            time_unit = 1e-3
            while True:
                line = s.readline()
                # Every line should have a "\n".  "" means end of file.
                if line == "":
                    break

                if line.startswith("Beam Current"):
                    # The viewer still supports metadata even if the harvester
                    # dropped that support.  I don't remember exactly what the
                    # '@ 1e1(1e1)' portion means, but lets make this work at
                    # least
                    tokens = line.strip().split(" ")
                    nline = f"# BeamCurent_uA={tokens[5]} @ -1.0e1(1.0e1)\n"
                elif line.startswith("deltaT"):
                    # The viewer still supports metadata even if the harvester
                    # dropped that support.  I don't remember exactly what the
                    # '@ 1e1(1e1)' portion means, but lets make this work at
                    # least
                    tokens = line.strip().split("\t")
                    nline = f"# deltaT={tokens[1]} @ -1.0e1(1.0e1)\n"

                    # Convert the deltaT to milliseconds
                    deltaT = float(tokens[1])
                    deltaT = deltaT / time_unit

                    # Now we start the time at 0.0 ms
                    time = 0.0
                elif "WireSum" in line:
                    nline = f"Time\t{line}"
                else:
                    # We should be pass the head and column names now.
                    # Make sure we have the needed metadata.
                    if deltaT is None:
                        raise RuntimeError("deltaT not defined in header.")

                    nline = f"{time:.4e}\t{line}"
                    count += 1
                    time = deltaT * count
                d.write(nline)    


def main():

    parser = argparse.ArgumentParser()
    parser.add_argument("-d", "--dir", type=str, 
                        help="Root data directory that contains <location>/<capture_file>")
    parser.add_argument("-o", "--out-dir", type=str,
                         help="Location of directory with converted data")
    
    args = parser.parse_args()

    if os.path.exists(args.out_dir):
        raise ValueError(f"{args.out_dir}: already exists")
    
    if not os.path.isdir(args.dir):
        raise ValueError(f"{args.dir}: not found")

    os.makedirs(args.out_dir)

    for location in os.listdir(args.dir):
        location_dir = args.out_dir + "/" + "cebaf"
        if not os.path.exists(location_dir):
            os.mkdir(location_dir)
        for file in os.listdir(args.dir + "/" + location):
            tokens = file.split("_")
            if len(tokens) != 3:
                print(f"Error: unsupported filename format - {file}")
            classification = tokens[0].lower()
            date = tokens[1]
            date = f"{date[0:4]}_{date[4:6]}_{date[6:8]}"
            time_ext_tokens = tokens[2].split(".")
            time = time_ext_tokens[0] + "." + time_ext_tokens[1]
            ext = time_ext_tokens[2]

            # The BPM data looks to have a single classification of FSD.  Implies there
            # may be other classifications in the future
            capture_dir = location_dir + "/" + classification + "/" + date + "/" + time
            
            if not os.path.isdir(capture_dir):
                os.makedirs(capture_dir)

            fname = f"{location}.{date}_{time}.{ext}"
            src = f"{args.dir}/{location}/{file}"
            dst = f"{capture_dir}/{fname}"
            convert_file(src=src, dst=dst)


if __name__ == "__main__":
    main()
