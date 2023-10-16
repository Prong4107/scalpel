export _DO_NOT_IMPORT_JAVA=1
PREFIX=scalpel/src/main/resources/python
cd $PREFIX
python3 -m unittest pyscalpel/tests/test_*.py qs/tests.py
