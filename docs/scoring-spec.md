BAIL = 50, POCSO = 50, MATRIMONIAL = 25, CRIMINAL_OTHER = 20, CIVIL = 10
Aging: 0.10 pts per day pending, capped at 40
Adjournments: 5.00 pts each, capped at 30 (adds to score; "penalty" is just a name)
Linked case bonus: 10 pts flat if the case has a linked case
Max theoretical score: 130
totalScore = caseTypeUrgency + min(days*0.10, 40) + min(adjournments*5.00, 30) + (hasLinkedCase ? 10 : 0)

*Note: Statutory deadline proximity factor was considered but dropped for Phase 1/Phase 2 to prioritize core MVP capabilities.*
