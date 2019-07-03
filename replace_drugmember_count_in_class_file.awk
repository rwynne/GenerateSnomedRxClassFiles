BEGIN {FS="|";OFS="|"}
{
#    drugMemberCount = ARGV[0];
#    if ($5=="N0000000003") name = "Pharmacokinetics (PK)";
#    else if ($5=="N0000000223") name = "Mechanism of Action (MoA)";
#    else if ($5=="N0000009802") name = "Physiologic Effect (PE)";
#    else if ($5=="N0000189939") name = "Established Pharmacologic Class (EPC)";
#    else if ($5=="D020228") name = "MeSH Pharmacologic Actions (MESHPA)";
#    else if ($5=="0") name = "Anatomical Therapeutic Chemical (ATC1-4)";
#    else if ($5=="N0000010574") name = "VA Classes (VA)";
#    else if ($5=="Z1") name = "Substances and Cells (CHEM)";
#    else if ($5=="Z2") name = "Substances";
#    else if ($5=="Z3") name = "Cells";
#    else if ($5=="Z4") name = "Other Substances";
#    else if ($5=="X1") name = "Diseases, Life Phases and Physiologic States";
#    else if ($5=="X2") name = "Life Phases and Physiologic States";
    print $1,$2,$3,mp,$5,count,$7,$8,$9,$10;
}

