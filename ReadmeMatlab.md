
# Overview

**ca** can also be easily used for Channel Access from within Matlab.


# Usage
As **ca** requires Java 8 or later please make sure that the you point your Matlab to a suitable Java version. This can be done e.g. by setting the `MATLAB_JAVA` environment variable before starting Matlab from the Command Line

```bash
export MATLAB_JAVA=/opt/gfa/java/latest/jre
```

After this download and copy the latest released version of the `ca-all-x.x.x.jar` library jar, available in the [releases](https://github.com/channelaccess/ca/releases) section into your Matlab workspace.

Afterwards the library can be used as follows:


```Matlab
javaaddpath('ca-all-1.0.1.jar')
import org.epics.ca.*

context = Context();
channel = Channels.create(context, 'S10CB01-RBOC-DCP10:FOR-AMPLT-MAX');

channel.get()


% Get metadata for channels as described in https://github.com/channelaccess/ca#metadata
value = channel.get(org.epics.ca.data.Graphic().getClass())
value.getUnits()

channel.close()
context.close()
```

For more details on the available functions please refer to the main [Readme.md](Readme.md) of the library.
