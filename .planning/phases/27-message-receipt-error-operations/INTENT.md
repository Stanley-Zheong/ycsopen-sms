# Phase 27 Intent

Operators need one place to inspect what happened to a message and safely perform recovery actions. The implementation should make the record trail clearer without making the system more complex.

Intent constraints:

- preserve original evidence;
- avoid duplicate downstream contact;
- keep protected input hidden;
- require explicit reasons for state-changing operations;
- return partial outcomes for bulk work instead of all-or-nothing ambiguity.
