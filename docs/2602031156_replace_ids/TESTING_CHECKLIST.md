# Phase 3 Testing Checklist

This checklist verifies all requirements from the technical specification are met.

## Command Syntax Reference

**Count Regions:**
```bash
java -jar build/libs/gff3tools-1.0-all.jar process count-regions [input-file]
```

**Replace IDs:**
```bash
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions ACC1,ACC2,... [-o output-file] [input-file]
```

Note: Use `-o` flag for output file. Without `-o`, output goes to stdout.

## Prerequisites
- [ ] Project builds successfully: `./gradlew clean build`
- [ ] All unit tests pass: `./gradlew test`
- [ ] Shadow JAR created: `build/libs/gff3tools-1.0-all.jar` exists

## R1: Count Sequence Regions

- [ ] Test 1: Count zero regions
```bash
echo -e '##gff-version 3\n' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions test.gff3
# Expected: 0
```

- [ ] Test 2: Count multiple regions
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 100\n##sequence-region S2 1 200\n##sequence-region S3 1 300' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions test.gff3
# Expected: 3
```

- [ ] Test 3: Memory efficiency (large file)
```bash
# Create file with 10,000 sequence regions
./gradlew test --tests ProcessCommandPerformanceTest
# Should complete without OutOfMemoryError
```

## R2: Replace Sequence Region IDs

- [ ] Test 4: Basic replacement
```bash
echo -e '##gff-version 3\n##sequence-region OLD1 1 100\nOLD1\tENA\tgene\t1\t50\t.\t+\t.\tID=g1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW1 -o out.gff3 test.gff3
grep "NEW1" out.gff3
# Expected: Both directive and feature line contain NEW1
grep "OLD1" out.gff3
# Expected: No matches
```

## R3: Sequential Mapping

- [ ] Test 5: Order preservation
```bash
echo -e '##gff-version 3\n##sequence-region Z 1 1\n##sequence-region A 1 2\n##sequence-region M 1 3' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions FIRST,SECOND,THIRD -o out.gff3 test.gff3
grep "##sequence-region" out.gff3
# Expected: FIRST appears first, then SECOND, then THIRD (not alphabetical)
```

## R4: Update All References

- [ ] Test 6: Feature line seqid updated
```bash
echo -e '##gff-version 3\n##sequence-region OLD 1 1000\nOLD\tENA\tgene\t1\t100\t.\t+\t.\tID=g1\nOLD\tENA\tCDS\t1\t100\t.\t+\t0\tID=c1;Parent=g1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW -o out.gff3 test.gff3
grep -c "^NEW\t" out.gff3
# Expected: 2 (both gene and CDS lines)
```

## R5: Preserve FASTA Section

- [ ] Test 7: FASTA unchanged
```bash
echo -e '##gff-version 3\n##sequence-region OLD 1 100\n##FASTA\n>seq1\nATGC\n>OLD\nGGGG' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW -o out.gff3 test.gff3
grep "##FASTA" out.gff3
grep ">seq1" out.gff3
grep ">OLD" out.gff3
# Expected: FASTA section completely unchanged, including >OLD header
```

## R6: Version Number Handling

- [ ] Test 8: Versions removed
```bash
echo -e '##gff-version 3\n##sequence-region ACC.1 1 100\n##sequence-region DEF.23 1 200\nACC.1\tENA\tgene\t1\t50\t.\t+\t.\tID=g1\nDEF.23\tENA\tgene\t1\t50\t.\t+\t.\tID=g2' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW1,NEW2 -o out.gff3 test.gff3
grep "\.1" out.gff3 || echo "No version numbers found (correct)"
grep "NEW1" out.gff3
grep "NEW2" out.gff3
# Expected: No .1 or .23, only NEW1 and NEW2
```

## R7: Strict Count Validation

- [ ] Test 9: Too few accessions
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1\n##sequence-region S2 1 2' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions ONLY1 -o out.gff3 test.gff3
echo $?
# Expected: Exit code 2 (USAGE)
```

- [ ] Test 10: Too many accessions
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions A1,A2,A3 -o out.gff3 test.gff3 2>&1 | grep -i "expected\|provided"
# Expected: Error message showing mismatch
echo $?
# Expected: Exit code 2
```

- [ ] Test 11: No output file on validation failure
```bash
rm -f out.gff3
echo -e '##gff-version 3\n##sequence-region S1 1 1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions A1,A2 -o out.gff3 test.gff3
test -f out.gff3 && echo "FAIL: Output file created" || echo "PASS: No output file"
# Expected: PASS
```

## R8: Stdin/Stdout Support

- [ ] Test 12: count-regions from stdin
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1\n##sequence-region S2 1 2' | java -jar build/libs/gff3tools-1.0-all.jar process count-regions
# Expected: 2
```

- [ ] Test 13: replace-ids stdin to stdout
```bash
echo -e '##gff-version 3\n##sequence-region OLD 1 100\nOLD\tENA\tgene\t1\t50\t.\t+\t.\tID=g1' | java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW | grep "NEW"
# Expected: Output contains NEW
```

- [ ] Test 14: Full pipe workflow
```bash
cat test.gff3 | java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW > out.gff3
grep "NEW" out.gff3
# Expected: Replacement successful
```

## R9: Non-Empty Accession Validation

- [ ] Test 15: Empty accession rejected
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions "" -o out.gff3 test.gff3 2>&1 | grep -i "empty\|blank"
# Expected: Error message about empty accession
```

- [ ] Test 16: Blank accession rejected
```bash
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions "   " -o out.gff3 test.gff3 2>&1 | grep -i "empty\|blank"
# Expected: Error message
```

## R9b: Accession Input Format

- [ ] Test 17: Whitespace trimming
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1\n##sequence-region S2 1 2' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions " A1 , A2 " -o out.gff3 test.gff3
grep "A1" out.gff3 | grep -v " A1" # Should not have leading/trailing spaces
# Expected: Clean accessions without extra whitespace
```

## R10: Logging Behavior

- [ ] Test 18: File output includes logs
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW -o out.gff3 test.gff3 2>&1 | grep -i "replac"
# Expected: Log messages visible on stderr
```

- [ ] Test 19: Stdout output suppresses info logs
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 1\nS1\tENA\tgene\t1\t50\t.\t+\t.\tID=g1' | java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW | grep -i "INFO"
# Expected: No INFO in stdout
```

## R11: Input Validation

- [ ] Test 20: Invalid GFF3 rejected
```bash
echo "Not a GFF3 file" > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process count-regions test.gff3
echo $?
# Expected: Exit code 20 (VALIDATION_ERROR)
```

## R12: Idempotency

- [ ] Test 21: Idempotent replacement
```bash
echo -e '##gff-version 3\n##sequence-region S1 1 100' > test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW -o out1.gff3 test.gff3
java -jar build/libs/gff3tools-1.0-all.jar process replace-ids --accessions NEW -o out2.gff3 out1.gff3
diff out1.gff3 out2.gff3
# Expected: Files are identical
```

## Success Criteria (from Spec)

- [ ] `java -jar gff3tools.jar process count-regions file.gff3` outputs single integer
- [ ] `cat file.gff3 | java -jar gff3tools.jar process count-regions` works via stdin
- [ ] `java -jar gff3tools.jar process replace-ids --accessions ACC1,ACC2 -o output.gff3 file.gff3` succeeds
- [ ] All features reference new accessions in column 1
- [ ] `##sequence-region` directives updated with new accessions
- [ ] `cat file.gff3 | java -jar gff3tools.jar process replace-ids --accessions ACC1,ACC2 > output.gff3` works
- [ ] Exit code 2 when fewer accessions than regions
- [ ] Exit code 2 when more accessions than regions
- [ ] Error message shows expected vs. provided count
- [ ] No output file created on validation failure
- [ ] Exit code 20 when input is not valid GFF3
- [ ] FASTA section unchanged after replacement
- [ ] Sequence regions with versions replaced with versionless accessions
- [ ] Empty/blank accessions rejected with clear error
- [ ] Whitespace around commas trimmed
- [ ] Stdout mode: only warnings/errors on stderr
- [ ] File mode: each replacement logged with summary

## Cleanup
```bash
rm -f test.gff3 out.gff3 out1.gff3 out2.gff3
```

## Final Verification

- [ ] All tests above pass
- [ ] `./gradlew build` succeeds
- [ ] `./gradlew test` all tests pass
- [ ] Documentation is complete and accurate
- [ ] No TODOs or FIXMEs in code
- [ ] Copyright headers on all new files
- [ ] Code follows project conventions (Lombok, Slf4j)
