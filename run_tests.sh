SRC="
scalpel/src/main/resources/python/pyscalpel/http/mime.py
scalpel/src/main/resources/python/pyscalpel/http/body.py
scalpel/src/main/resources/python/qs/qs.py
"

export _DO_NOT_IMPORT_JAVA="yes"
export PATH="scalpel/src/main/resources/python:$PATH"
for file in $SRC;do
    python3 "$file"
done