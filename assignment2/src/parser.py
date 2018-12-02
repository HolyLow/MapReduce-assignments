#!/bin/python3

# import re
from scanf import scanf
import sys
if __name__ == '__main__':
# 	pattern = re.compile(r'\(\S+, \S+\)\t[-+]?(\d+(\.\d*)?|\.\d+)([eE][-+]?\d+)?')
	pair_pool = set()
	pmi_pool = set()
	pattern = '(%s, %s)\t%f'
	filename = sys.argv[1]
	for line in open(filename, "r"):
		item = scanf(pattern, line)
		pmi_pool.add(item[2])
		pair_pool.add(item)
	max_pmi = -1000000000.00
	for pmi in pmi_pool:
		if max_pmi < pmi:
			max_pmi = pmi
	print("pmi_num = %d" % len(pmi_pool))
	print("max_pmi = %f" % max_pmi)
	print("max_pmi_pairs: ")
	for pair in pair_pool:
		if pair[2] == max_pmi:	
			print("%s, %s" % (pair[0], pair[1]))

