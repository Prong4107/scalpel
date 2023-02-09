import sys

print('This goes in stdout')
print('This goes in stderr', file=sys.stderr)

from datetime import datetime

print(datetime.now())

import numpy as np
a = np.arange(15).reshape(3, 5)

print(a)
