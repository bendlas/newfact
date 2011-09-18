(ns newfact.test.analyze
  (:use [newfact.analyze :only (references)]
	[newfact.test.utils :only (is)]
        [lazytest.describe :only (describe it)]))

(describe "analysing simple forms"
  (it "returns an empty list when asking for references of a def"
    (is = (references '(def a {})) '())))
