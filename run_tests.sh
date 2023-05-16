SRC="
pyscalpel/http/mime.py
pyscalpel/http/body/tests.py
qs/qs.py
"

export _DO_NOT_IMPORT_JAVA="yes"
for file in $SRC;do
    echo "$file":
    path="scalpel/src/main/resources/python/$file"
    python3 "$path"  #|| break
done