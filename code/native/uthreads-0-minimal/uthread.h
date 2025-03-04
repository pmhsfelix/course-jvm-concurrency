#ifndef _UTHREAD_H_
#define _UTHREAD_H_

#include <stdint.h>

/**
 * Thread descriptor - structure with information about a thread.
 */
typedef struct uthread uthread_t;
/**
 * Type of the thread entry point - just a function receiving a uint64_t
 */
typedef void (*start_routine_t)(uint64_t);

/**
 * Initializes the uthreads system.
 */
void ut_init();

/**
 * Creates a new thread in the ready state.
 * - allocate space for the thread descriptor
 * - allocate space for the thread's stack
 * - Note: in uthreads-0-minimal, both the thread descriptor and the thread stack
 *         will be in the same allocated block.
 * - initialize the stack
 *   remember the context_switch *assumes* that the stack of the next thread 
 *   has the context put there by a previous context_switch
 * - adds the thread descriptor to the tail of the ready queue.
 */
uthread_t *ut_create(start_routine_t, uint64_t arg);

/**
 * Runs the uthreads system, returning when there aren't more threads alive.
 */
void ut_run();

/**
 * Moves the running thread to the ready state.
 * If there aren't any other threads in the ready state, then the running thread continues in the running state.
 */
void ut_yield();

#endif