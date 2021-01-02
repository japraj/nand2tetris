# nand2tetris

This repo is a collection of software projects from the open-source nand2tetris course. The course consists of two parts:

- building a 16-bit computer, starting from nand gates (we design an ALU, CPU, RAM module, and all the chips that compose these units) - this is done using a custom flavor of VHDL/Verilog

- building a software platform for the aforementioned computer; this consists of an assembler, virtual machine, compiler (for a high-level language unique to our computer), and an operating system

# Descriptions of the software projects:

Assembler: translates machine language from its (human-readable) symbolic representation to its binary representation, consuming assembly files and producing binary files

- it is based on the ASM specification/grammar provided in the course (the spec is based on the 16-bit computer we built in the first half of the course)

- first, we remove all comments and whitespace from the file. Next, we do an initial pass over the set of instructions, enumerating all goto labels (pseudo-instructions). Finally, we parse and map the remaining instructions one-by-one

- we decompose computation instructions into fields and convert these to binary individually. Additionally, we map symbols/variables (called address instructions) to addresses that point to instruction/data registers. Note that we dynamically allocate memory for variables

Virtual Machine: translates intermediate bytecode, written for a stack-based virtual machine, to ASM

- the VM translator works by mapping each VM command to a set of ASM instructions; examples of vm commands are: "push local 0", "pop static 1", "add", "sub", "goto label", and "function foo"; the VM code is a lot more readable than ASM and it lets us abstract away the low level operations to some degree (used as the backend of a compiler later)

- the virtual machine works by partitioning our RAM into a set of segments, for storing arguments, local/static/temporary variables, etc, and by mantaining a global stack which allows push and pop operations w.r.t. these segments

- ex. 'push local 1' means "push the value at index 1 in the local variables segment onto the stack", and "pop temp 2" means "pop the stack and store the result in index 2 of the temp. vars segment"

- the stack stores everything from intermediate values to memory segments and even function data (ex. return addresses and pointers), allowing for nested calls, recursion, and an elegant call heirarchy
