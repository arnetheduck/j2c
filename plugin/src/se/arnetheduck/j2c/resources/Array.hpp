#pragma once

#include <algorithm>
#include <initializer_list>

#include <fwd.hpp>

#include <java/lang/Object.hpp>
#include <java/lang/Cloneable.hpp>
#include <java/io/Serializable.hpp>

template<typename T>
class Array
    : public virtual ::java::lang::Object
    , public virtual ::java::lang::Cloneable
    , public virtual ::java::io::Serializable
{
public:
    static ::java::lang::Class *class_();

    typedef T value_type;
    typedef value_type *pointer_type;
    typedef int size_type;

    Array() : length(0), p(nullptr) { }
    Array(int n) : length(n), p(new value_type[n]) { }

    Array(const value_type *p, int n) : length(n), p(new value_type[n])
    {
    	std::copy(p, p+n, this->p);
    }

    template<typename S>
    Array(std::initializer_list<S> l) : length(l.size()), p(new value_type[l.size()])
    {
    	std::copy(l.begin(), l.end(), p);
    }

    Array(const Array &rhs) : length(rhs.length), p(new value_type[rhs.length])
    {
    	std::copy(rhs.p, rhs.p + rhs.length, p);
    }

    Array(Array &&rhs) : length(rhs.length), p(rhs.p)
    {
    	const_cast<pointer_type&>(rhs.p) = 0;
    }

    Array &operator=(const Array &rhs)
    {
    	if(&rhs != this) {
			delete p;
			const_cast<pointer_type&>(p) = 0;
			const_cast<size_type&>(length) = rhs.length;
			const_cast<pointer_type&>(p) = new value_type[length];
			std::copy(rhs.p, rhs.p + length, p);
    	}

    	return *this;
    }

    Array &operator=(Array &&rhs)
    {
    	if(&rhs != this) {
			delete p;
			const_cast<size_type&>(length) = rhs.length;
			const_cast<pointer_type&>(p) = rhs.p;
			const_cast<pointer_type&>(rhs.p) = 0;
    	}

    	return *this;
    }

    virtual ~Array() { delete p; }

    Array* clone() override { return new Array(*this); }

    value_type operator[](size_type i) const { return p[i]; }
    value_type &operator[](size_type i) { return p[i]; }

    value_type get(size_type i) const { return p[i]; }
    value_type &set(size_type i, value_type x) { return (p[i] = x); }

    const size_type length;
    const pointer_type p;

private:
     ::java::lang::Class *getClass0() override;
};
