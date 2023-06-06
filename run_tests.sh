export _DO_NOT_IMPORT_JAVA=1
PREFIX=scalpel/src/main/resources/python
python3 -m unittest $PREFIX/pyscalpel/tests/*.py $PREFIX/qs/qs.py 
