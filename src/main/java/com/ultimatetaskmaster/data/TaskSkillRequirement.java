package com.ultimatetaskmaster.data;

import lombok.Value;

/**
 * A skill level requirement for a task.
 * Immutable value object — just a (skill, level) pair.
 */
@Value
public class TaskSkillRequirement
{
	String skill;
	int level;
}
