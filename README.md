# nand2tetris

This repo is a collection of my solutions to software projects from the open-source nand2tetris course. The course consists of two parts:

- building a 16-bit computer, starting from nand gates (we design an ALU, CPU, RAM module, and all the chips that compose these units) - this is done using a custom flavor of VHDL/Verilog

- building a software platform for the aforementioned computer; this consists of an assembler, virtual machine, compiler (for a high-level language unique to our computer), and an operating system

# Descriptions of the software projects:

**Assembler:** translates machine language from its (human-readable) symbolic representation to its binary representation, consuming assembly files and producing binary files

- it is based on the ASM grammar provided in the book (the specification itself is based on the 16-bit computer we built in the first half of the course)

- first, it removes all comments/whitespace from the file, then it does an initial pass over the set of instructions, enumerating all goto labels, and finally, it parses and maps the remaining instructions one-by-one

- we decompose instructions into their component fields and convert these to binary individually, to get 16-bit instructions. Additionally, we dynamically allocate registers for variables/symbols as we encounter them

**Virtual Machine:** translates intermediate bytecode, written for a stack-based virtual machine, to ASM

- the VM translator works by mapping each VM command to a set of ASM instructions; examples of vm commands are: "push local 0", "pop static 1", "add", "sub", "goto label", and "call Math.multiply 2"; the VM code is a lot more readable than ASM and it lets us abstract away the low level operations to some degree (used as the backend of a compiler later)

- the virtual machine works by partitioning our RAM into a set of segments, for storing arguments, local/static/temporary variables, etc, and by mantaining a global stack which allows push and pop operations w.r.t. these segments

- ex. 'push local 1' means "push the value at index 1 in the local variables segment onto the stack", and "pop temp 2" means "pop the stack and store the result in index 2 of the temp. vars segment"

- the stack stores everything from intermediate values to memory segments and even function data (ex. return addresses and pointers), allowing for nested calls, recursion, and an elegant call heirarchy

**Syntax Analyzer:** parses source files written in Jack, a language based on Java, and produces xml files showing the structure of the file with various tags that label semantics

- this project forms the basis for our compiler; we reuse the tokenization and parsing modules (and add a few more)
- the tokenizer breaks source files into token streams; a token is any symbol, keyword, literal (int/string constant), or identifier. It is based on the Jack specification
- the main item, the top-down recursive descent parser, uses the tokenizer as an API to Jack source files, and parses/labels collections of tokens according to a context-free grammar provided in the textbook

**Compiler:** translates source files written in Jack to VM bytecode; this is the "front-end" of our two-tier compilation

- an extension of the analyzer, this project handles memory allocation (using OS services), expression evaluation, subroutine calling, and variable declarations
- for variable declarations, we use a Symbol Table that keeps track of information regarding local/static vars, fields, and arguments
- makes heavy use of features of our VM (for example, virtual memory segments) to manage objects and arrays (array indexing is very elegant!)
